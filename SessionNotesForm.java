package rebirth;

import java.awt.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Counsellor only: write notes for a session, rate the prisoner's progress,
 * add a follow-up action and (optionally) mark the session Completed.
 *
 * Notes are stored in COUNSELLING_SESSIONS.NOTES and are never shown to
 * the prisoner (ScheduleSessionForm does not select that column).
 */
public class SessionNotesForm extends JFrame {

    private static final String WHEN = "dd-MMM-yyyy  hh:mm a";

    private final int userId;
    private int counsellorId;

    private final JComboBox<String> filterBox =
        CounsellorUtil.combo("All", "Scheduled", "Completed", "Cancelled");
    private final JLabel selected = new JLabel("Select a session from the table.");
    private final JTextArea notes = Theme.area(7);
    private final JComboBox<String> rating = CounsellorUtil.combo(
        "1 - Needs attention", "2 - Fair", "3 - Good",
        "4 - Very good", "5 - Excellent");
    private final JTextField followUp = Theme.field();
    private final JCheckBox markDone =
        new JCheckBox("Mark this session as Completed", true);

    private final DefaultTableModel model;
    private final JTable table;

    public SessionNotesForm(int userId, String fullName, String role) {

        this.userId = userId;

        setTitle("REbirth - Session Notes");
        setSize(1100, 900);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        model = CounsellorUtil.readOnlyModel(
            "ID", "Prisoner", "Date & Time", "Topic", "Status");
        table = CounsellorUtil.table(model);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);

        try (Connection con = DBConnection.getConnection()) {
            CounsellorUtil.ensureTables(con);
            counsellorId = CounsellorUtil.counsellorId(con, userId);
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        selected.setFont(Theme.f(Font.BOLD, 14));
        selected.setForeground(Theme.GREEN_DARK);
        markDone.setOpaque(false);
        markDone.setFont(Theme.f(Font.PLAIN, 13));

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("Session Notes",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        // ---- top: filter + table
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        JLabel fl = new JLabel("Show:");
        fl.setFont(Theme.f(Font.BOLD, 13));
        filterRow.add(fl);
        filterRow.add(filterBox);
        filterBox.addActionListener(e -> loadSessions());

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(filterRow, BorderLayout.NORTH);
        JScrollPane tableScroll = CounsellorUtil.scroll(table);
        tableScroll.setPreferredSize(new Dimension(100, 220));
        top.add(tableScroll, BorderLayout.CENTER);

        // ---- bottom: notes form
        JPanel card = CounsellorUtil.formCard();
        CounsellorUtil.addRow(card, 0, "Session", selected);
        JScrollPane notesScroll = Theme.scroll(notes);
        notesScroll.setPreferredSize(new Dimension(500, 230));
        notesScroll.setMinimumSize(new Dimension(300, 170));
        CounsellorUtil.addTallRow(card, 1, "Notes", notesScroll);
        CounsellorUtil.addRow(card, 2, "Progress", rating);
        CounsellorUtil.addRow(card, 3, "Follow-up action", followUp);
        CounsellorUtil.addRow(card, 4, "", markDone);

        Theme.Button save = new Theme.Button("Save Notes", Theme.GREEN, Color.WHITE);
        Theme.Button clear = new Theme.Button("Clear",
            new Color(180, 120, 145), Color.WHITE);
        save.addActionListener(e -> saveNotes());
        clear.addActionListener(e -> clearForm());
        CounsellorUtil.addRow(card, 5, "", CounsellorUtil.buttonRow(save, clear));

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        body.add(top, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadDetails();
            }
        });

        loadSessions();
    }

    // ------------------------------------------------------------------
    private void loadSessions() {

        model.setRowCount(0);
        String filter = filterBox.getSelectedItem().toString();

        String sql = "SELECT s.SESSION_ID, u.FULL_NAME, s.SESSION_DATE, "
            + "s.TOPIC, s.STATUS FROM COUNSELLING_SESSIONS s "
            + "JOIN PRISONER_DETAILS p ON p.PRISONER_ID = s.PRISONER_ID "
            + "JOIN USERS u ON u.USER_ID = p.USER_ID "
            + "WHERE s.COUNSELLOR_ID = ? AND (? = 'All' OR UPPER(s.STATUS) = UPPER(?)) "
            + "ORDER BY s.SESSION_DATE DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, counsellorId);
            ps.setString(2, filter);
            ps.setString(3, filter);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt(1), rs.getString(2),
                        CounsellorUtil.fmt(rs.getTimestamp(3), WHEN),
                        rs.getString(4), rs.getString(5)
                    });
                }
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
        clearForm();
    }

    private void loadDetails() {

        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);
        selected.setText(model.getValueAt(row, 1) + "  -  "
            + model.getValueAt(row, 3) + "  (" + model.getValueAt(row, 4) + ")");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT NOTES, PROGRESS_RATING, FOLLOW_UP "
                 + "FROM COUNSELLING_SESSIONS "
                 + "WHERE SESSION_ID = ? AND COUNSELLOR_ID = ?")) {

            ps.setInt(1, id);
            ps.setInt(2, counsellorId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    notes.setText(Theme.nz(rs.getString(1)));
                    int r = rs.getInt(2);
                    rating.setSelectedIndex(r >= 1 && r <= 5 ? r - 1 : 2);
                    followUp.setText(Theme.nz(rs.getString(3)));
                    notes.setCaretPosition(0);
                }
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        markDone.setSelected(
            !"Completed".equalsIgnoreCase(String.valueOf(model.getValueAt(row, 4))));
    }

    private void saveNotes() {

        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a session from the table.");
            return;
        }

        String status = String.valueOf(model.getValueAt(row, 4));
        if (status.equalsIgnoreCase("Cancelled")) {
            CounsellorUtil.error(this, "A cancelled session cannot have notes.");
            return;
        }

        String text = notes.getText().trim();
        if (text.isEmpty()) {
            CounsellorUtil.error(this, "Enter the session notes.");
            return;
        }
        if (followUp.getText().trim().length() > 500) {
            CounsellorUtil.error(this, "Follow-up action is too long (max 500).");
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);
        String newStatus = markDone.isSelected() ? "Completed" : status;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE COUNSELLING_SESSIONS SET NOTES = ?, PROGRESS_RATING = ?, "
                 + "FOLLOW_UP = ?, STATUS = ? "
                 + "WHERE SESSION_ID = ? AND COUNSELLOR_ID = ?")) {

            ps.setString(1, text);
            ps.setInt(2, rating.getSelectedIndex() + 1);
            ps.setString(3, followUp.getText().trim());
            ps.setString(4, newStatus);
            ps.setInt(5, id);
            ps.setInt(6, counsellorId);
            ps.executeUpdate();

            CounsellorUtil.info(this, "Notes saved.");
            loadSessions();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void clearForm() {
        table.clearSelection();
        selected.setText("Select a session from the table.");
        notes.setText("");
        followUp.setText("");
        rating.setSelectedIndex(2);
        markDone.setSelected(true);
    }
}