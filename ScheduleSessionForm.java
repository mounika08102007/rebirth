package rebirth;

import java.awt.*;
import java.sql.*;
import java.util.Calendar;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Counsellor : schedule a session, mark it Completed or Cancelled.
 * Prisoner   : read-only list of their own sessions
 *              (open it with the same constructor; the role decides the mode).
 *
 * Table: COUNSELLING_SESSIONS (created automatically if missing).
 * Status values: Scheduled, Completed, Cancelled
 * (the dashboard growth tree looks for 'Completed').
 */
public class ScheduleSessionForm extends JFrame {

    private static final String WHEN = "dd-MMM-yyyy  hh:mm a";

    private final int userId;
    private final boolean counsellor;
    private int counsellorId;
    private int prisonerId;

    private final JComboBox<CounsellorUtil.Item> prisonerBox =
        Theme.flat(new JComboBox<CounsellorUtil.Item>());
    private final JSpinner dateSpin;
    private final JComboBox<String> modeBox =
        CounsellorUtil.combo("In-person", "Video call", "Phone call");
    private final JTextField topic = Theme.field();

    private final DefaultTableModel model;
    private final JTable table;

    public ScheduleSessionForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.counsellor = role.equalsIgnoreCase("Counsellor");

        setTitle(counsellor ? "REbirth - Schedule Session"
                            : "REbirth - My Counselling Sessions");
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // default = tomorrow 10:00 AM
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 10);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        dateSpin = CounsellorUtil.dateSpinner(c.getTime(), WHEN);

        model = CounsellorUtil.readOnlyModel("ID",
            counsellor ? "Prisoner" : "Counsellor",
            "Date & Time", "Mode", "Topic", "Status");
        table = CounsellorUtil.table(model);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);

        // find who is logged in
        try (Connection con = DBConnection.getConnection()) {
            CounsellorUtil.ensureTables(con);
            if (counsellor) {
                counsellorId = CounsellorUtil.counsellorId(con, userId);
                for (CounsellorUtil.Item i : CounsellorUtil.prisoners(con)) {
                    prisonerBox.addItem(i);
                }
            } else {
                prisonerId = CounsellorUtil.prisonerId(con, userId);
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand(
            counsellor ? "Schedule Session" : "My Counselling Sessions",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        if (counsellor) {
            body.add(buildForm(), BorderLayout.NORTH);
        }
        body.add(CounsellorUtil.scroll(table), BorderLayout.CENTER);
        body.add(buildButtons(), BorderLayout.SOUTH);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        loadSessions();
    }

    // ------------------------------------------------------------------
    // Screen parts
    // ------------------------------------------------------------------
    private JPanel buildForm() {
        JPanel card = CounsellorUtil.formCard();
        CounsellorUtil.addRow(card, 0, "Prisoner", prisonerBox);
        CounsellorUtil.addRow(card, 1, "Date & Time", dateSpin);
        CounsellorUtil.addRow(card, 2, "Mode", modeBox);
        CounsellorUtil.addRow(card, 3, "Topic", topic);
        return card;
    }

    private JPanel buildButtons() {

        Theme.Button refresh = new Theme.Button("Refresh",
            new Color(120, 130, 150), Color.WHITE);
        refresh.addActionListener(e -> loadSessions());

        if (!counsellor) {
            return CounsellorUtil.buttonRow(refresh);
        }

        Theme.Button schedule = new Theme.Button("Schedule Session",
            Theme.GREEN, Color.WHITE);
        Theme.Button done = new Theme.Button("Mark Completed",
            Theme.GREEN_DARK, Color.WHITE);
        Theme.Button cancel = new Theme.Button("Cancel Session",
            Theme.RED, Color.WHITE);

        schedule.addActionListener(e -> scheduleSession());
        done.addActionListener(e -> changeStatus("Completed"));
        cancel.addActionListener(e -> changeStatus("Cancelled"));

        return CounsellorUtil.buttonRow(schedule, done, cancel, refresh);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------
    private void scheduleSession() {

        CounsellorUtil.Item prisoner =
            (CounsellorUtil.Item) prisonerBox.getSelectedItem();
        if (prisoner == null) {
            CounsellorUtil.error(this, "Select a prisoner.");
            return;
        }

        java.util.Date when = (java.util.Date) dateSpin.getValue();
        if (when.before(new java.util.Date())) {
            CounsellorUtil.error(this, "Choose a future date and time.");
            return;
        }

        String topicText = topic.getText().trim();
        if (topicText.isEmpty()) {
            CounsellorUtil.error(this, "Enter the session topic.");
            return;
        }

        // drop seconds so two sessions at the "same time" really match
        Timestamp ts = new Timestamp(when.getTime() / 60000L * 60000L);

        try (Connection con = DBConnection.getConnection()) {

            CounsellorUtil.ensureTables(con);

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM COUNSELLING_SESSIONS "
                    + "WHERE COUNSELLOR_ID = ? AND SESSION_DATE = ? "
                    + "AND UPPER(STATUS) = 'SCHEDULED'")) {
                ps.setInt(1, counsellorId);
                ps.setTimestamp(2, ts);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        CounsellorUtil.error(this,
                            "You already have a session at that time.");
                        return;
                    }
                }
            }

            int id = CounsellorUtil.nextId(con, "COUNSELLING_SESSIONS", "SESSION_ID");

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO COUNSELLING_SESSIONS (SESSION_ID, PRISONER_ID, "
                    + "COUNSELLOR_ID, SESSION_DATE, SESSION_MODE, TOPIC, STATUS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'Scheduled')")) {
                ps.setInt(1, id);
                ps.setInt(2, prisoner.id);
                ps.setInt(3, counsellorId);
                ps.setTimestamp(4, ts);
                ps.setString(5, modeBox.getSelectedItem().toString());
                ps.setString(6, topicText);
                ps.executeUpdate();
            }

            CounsellorUtil.info(this, "Session scheduled.");
            topic.setText("");
            loadSessions();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void changeStatus(String newStatus) {

        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a session from the table.");
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);
        String current = String.valueOf(model.getValueAt(row, 5));

        if (!current.equalsIgnoreCase("Scheduled")) {
            CounsellorUtil.error(this, "Only a Scheduled session can be changed.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
            "Mark this session as " + newStatus + "?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE COUNSELLING_SESSIONS SET STATUS = ? "
                 + "WHERE SESSION_ID = ? AND COUNSELLOR_ID = ?")) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            ps.setInt(3, counsellorId);
            ps.executeUpdate();
            loadSessions();
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void loadSessions() {

        model.setRowCount(0);

        String sql;
        int idValue;

        if (counsellor) {
            idValue = counsellorId;
            sql = "SELECT s.SESSION_ID, u.FULL_NAME, s.SESSION_DATE, "
                + "s.SESSION_MODE, s.TOPIC, s.STATUS "
                + "FROM COUNSELLING_SESSIONS s "
                + "JOIN PRISONER_DETAILS p ON p.PRISONER_ID = s.PRISONER_ID "
                + "JOIN USERS u ON u.USER_ID = p.USER_ID "
                + "WHERE s.COUNSELLOR_ID = ? ORDER BY s.SESSION_DATE DESC";
        } else {
            idValue = prisonerId;
            // notes are NOT selected: counsellor notes stay private
            sql = "SELECT s.SESSION_ID, u.FULL_NAME, s.SESSION_DATE, "
                + "s.SESSION_MODE, s.TOPIC, s.STATUS "
                + "FROM COUNSELLING_SESSIONS s "
                + "JOIN COUNSELLOR_DETAILS c ON c.COUNSELLOR_ID = s.COUNSELLOR_ID "
                + "JOIN USERS u ON u.USER_ID = c.USER_ID "
                + "WHERE s.PRISONER_ID = ? ORDER BY s.SESSION_DATE DESC";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idValue);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt(1),
                        rs.getString(2),
                        CounsellorUtil.fmt(rs.getTimestamp(3), WHEN),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6)
                    });
                }
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }
}