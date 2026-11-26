package rebirth;

import java.awt.*;
import java.sql.*;
import java.util.Calendar;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Rehabilitation plan = a list of goals for one prisoner.
 *
 * Counsellor : pick a prisoner, add / update / delete goals.
 * Prisoner   : read-only view of their own goals with a progress bar.
 *
 * Table: REHABILITATION_PLANS (created automatically if missing).
 * Status values: Not Started, In Progress, Completed
 */
public class RehabilitationPlanForm extends JFrame {

    private static final String DAY = "dd-MMM-yyyy";

    private final int userId;
    private final boolean counsellor;
    private int counsellorId;
    private int ownPrisonerId;

    private final JComboBox<CounsellorUtil.Item> prisonerBox =
        Theme.flat(new JComboBox<CounsellorUtil.Item>());
    private final JComboBox<String> category = CounsellorUtil.combo(
        "Education", "Employment", "Health", "Family",
        "Legal", "Behaviour", "Skills", "Other");
    private final JTextField goal = Theme.field();
    private final JSpinner targetDate;
    private final JComboBox<String> status =
        CounsellorUtil.combo("Not Started", "In Progress", "Completed");
    private final JTextField remarks = Theme.field();

    private final Theme.ProgressBar progress = new Theme.ProgressBar();
    private final JLabel progressText = new JLabel(" ");

    private final DefaultTableModel model;
    private final JTable table;

    public RehabilitationPlanForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.counsellor = role.equalsIgnoreCase("Counsellor");

        setTitle(counsellor ? "REbirth - Update Rehabilitation Plan"
                            : "REbirth - My Rehabilitation Plan");
        setSize(1050, 740);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, 1);
        targetDate = CounsellorUtil.dateSpinner(c.getTime(), DAY);

        model = CounsellorUtil.readOnlyModel(
            "ID", "Category", "Goal", "Target Date", "Status", "Remarks");
        table = CounsellorUtil.table(model);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(320);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(220);

        try (Connection con = DBConnection.getConnection()) {
            CounsellorUtil.ensureTables(con);
            if (counsellor) {
                counsellorId = CounsellorUtil.counsellorId(con, userId);
                for (CounsellorUtil.Item i : CounsellorUtil.prisoners(con)) {
                    prisonerBox.addItem(i);
                }
            } else {
                ownPrisonerId = CounsellorUtil.prisonerId(con, userId);
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        progressText.setFont(Theme.f(Font.BOLD, 13));
        progressText.setForeground(Theme.GREEN_DARK);

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand(
            counsellor ? "Rehabilitation Plan" : "My Rehabilitation Plan",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        // ---- top (form for counsellor, nothing for prisoner)
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);

        if (counsellor) {
            JPanel card = CounsellorUtil.formCard();
            CounsellorUtil.addRow(card, 0, "Prisoner", prisonerBox);
            CounsellorUtil.addRow(card, 1, "Category", category);
            CounsellorUtil.addRow(card, 2, "Goal", goal);
            CounsellorUtil.addRow(card, 3, "Target Date", targetDate);
            CounsellorUtil.addRow(card, 4, "Status", status);
            CounsellorUtil.addRow(card, 5, "Remarks", remarks);
            top.add(card, BorderLayout.NORTH);

            prisonerBox.addActionListener(e -> {
                clearForm();
                loadPlan();
            });
            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    fillFormFromRow();
                }
            });
        }

        JPanel progressRow = new JPanel(new BorderLayout(12, 0));
        progressRow.setOpaque(false);
        progressRow.add(progressText, BorderLayout.WEST);
        progressRow.add(progress, BorderLayout.CENTER);
        top.add(progressRow, BorderLayout.SOUTH);

        body.add(top, BorderLayout.NORTH);
        body.add(CounsellorUtil.scroll(table), BorderLayout.CENTER);
        body.add(buildButtons(), BorderLayout.SOUTH);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        loadPlan();
    }

    private JPanel buildButtons() {

        Theme.Button refresh = new Theme.Button("Refresh",
            new Color(120, 130, 150), Color.WHITE);
        refresh.addActionListener(e -> loadPlan());

        if (!counsellor) {
            return CounsellorUtil.buttonRow(refresh);
        }

        Theme.Button add = new Theme.Button("Add Goal", Theme.GREEN, Color.WHITE);
        Theme.Button update = new Theme.Button("Update Selected",
            Theme.GREEN_DARK, Color.WHITE);
        Theme.Button delete = new Theme.Button("Delete Selected",
            Theme.RED, Color.WHITE);
        Theme.Button clear = new Theme.Button("Clear",
            new Color(180, 120, 145), Color.WHITE);

        add.addActionListener(e -> addGoal());
        update.addActionListener(e -> updateGoal());
        delete.addActionListener(e -> deleteGoal());
        clear.addActionListener(e -> clearForm());

        return CounsellorUtil.buttonRow(add, update, delete, clear, refresh);
    }

    // ------------------------------------------------------------------
    private int currentPrisonerId() {
        if (!counsellor) {
            return ownPrisonerId;
        }
        CounsellorUtil.Item p = (CounsellorUtil.Item) prisonerBox.getSelectedItem();
        return p == null ? 0 : p.id;
    }

    private static java.sql.Date toSqlDate(JSpinner s) {
        return new java.sql.Date(((java.util.Date) s.getValue()).getTime());
    }

    private boolean validGoal() {
        if (currentPrisonerId() == 0) {
            CounsellorUtil.error(this, "Select a prisoner.");
            return false;
        }
        String g = goal.getText().trim();
        if (g.isEmpty()) {
            CounsellorUtil.error(this, "Enter the goal.");
            return false;
        }
        if (g.length() > 300 || remarks.getText().trim().length() > 500) {
            CounsellorUtil.error(this,
                "Goal (max 300) or remarks (max 500) is too long.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    private void loadPlan() {

        model.setRowCount(0);
        int pid = currentPrisonerId();
        int total = 0;
        int done = 0;

        if (pid != 0) {
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "SELECT PLAN_ID, CATEGORY, GOAL, TARGET_DATE, STATUS, REMARKS "
                     + "FROM REHABILITATION_PLANS WHERE PRISONER_ID = ? "
                     + "ORDER BY TARGET_DATE, PLAN_ID")) {

                ps.setInt(1, pid);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        total++;
                        if ("Completed".equalsIgnoreCase(rs.getString(5))) {
                            done++;
                        }
                        Timestamp t = rs.getTimestamp(4);
                        model.addRow(new Object[]{
                            rs.getInt(1), rs.getString(2), rs.getString(3),
                            CounsellorUtil.fmt(t, DAY),
                            rs.getString(5), Theme.nz(rs.getString(6))
                        });
                    }
                }
            } catch (SQLException ex) {
                CounsellorUtil.error(this, ex.getMessage());
            }
        }

        progress.setValue(total == 0 ? 0 : (double) done / total);
        progressText.setText(total == 0 ? "No goals yet"
            : done + " of " + total + " goals completed");
    }

    private void addGoal() {

        if (!validGoal()) {
            return;
        }

        try (Connection con = DBConnection.getConnection()) {

            int id = CounsellorUtil.nextId(con, "REHABILITATION_PLANS", "PLAN_ID");

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO REHABILITATION_PLANS (PLAN_ID, PRISONER_ID, "
                    + "COUNSELLOR_ID, CATEGORY, GOAL, TARGET_DATE, STATUS, "
                    + "REMARKS, LAST_UPDATED) VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSDATE)")) {
                ps.setInt(1, id);
                ps.setInt(2, currentPrisonerId());
                ps.setInt(3, counsellorId);
                ps.setString(4, category.getSelectedItem().toString());
                ps.setString(5, goal.getText().trim());
                ps.setDate(6, toSqlDate(targetDate));
                ps.setString(7, status.getSelectedItem().toString());
                ps.setString(8, remarks.getText().trim());
                ps.executeUpdate();
            }

            clearForm();
            loadPlan();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void updateGoal() {

        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a goal from the table.");
            return;
        }
        if (!validGoal()) {
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE REHABILITATION_PLANS SET CATEGORY = ?, GOAL = ?, "
                 + "TARGET_DATE = ?, STATUS = ?, REMARKS = ?, COUNSELLOR_ID = ?, "
                 + "LAST_UPDATED = SYSDATE WHERE PLAN_ID = ? AND PRISONER_ID = ?")) {

            ps.setString(1, category.getSelectedItem().toString());
            ps.setString(2, goal.getText().trim());
            ps.setDate(3, toSqlDate(targetDate));
            ps.setString(4, status.getSelectedItem().toString());
            ps.setString(5, remarks.getText().trim());
            ps.setInt(6, counsellorId);
            ps.setInt(7, id);
            ps.setInt(8, currentPrisonerId());
            ps.executeUpdate();

            clearForm();
            loadPlan();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void deleteGoal() {

        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a goal from the table.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this, "Delete this goal?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "DELETE FROM REHABILITATION_PLANS "
                 + "WHERE PLAN_ID = ? AND PRISONER_ID = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, currentPrisonerId());
            ps.executeUpdate();
            clearForm();
            loadPlan();
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void fillFormFromRow() {

        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        category.setSelectedItem(model.getValueAt(row, 1));
        goal.setText(String.valueOf(model.getValueAt(row, 2)));
        status.setSelectedItem(model.getValueAt(row, 4));
        remarks.setText(String.valueOf(model.getValueAt(row, 5)));

        try {
            java.util.Date d = new java.text.SimpleDateFormat(DAY)
                .parse(String.valueOf(model.getValueAt(row, 3)));
            targetDate.setValue(d);
        } catch (java.text.ParseException ignored) {
            // no target date stored: keep the current value
        }
    }

    private void clearForm() {
        table.clearSelection();
        category.setSelectedIndex(0);
        goal.setText("");
        status.setSelectedIndex(0);
        remarks.setText("");
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, 1);
        targetDate.setValue(c.getTime());
    }
}