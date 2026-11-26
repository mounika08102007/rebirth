package rebirth;

import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Small helpers shared by the counsellor screens:
 * ScheduleSessionForm, SessionNotesForm and RehabilitationPlanForm.
 * Also creates the two new tables the first time they are needed.
 */
public final class CounsellorUtil {

    private CounsellorUtil() { }

    // ---------------------------------------------------------------
    // Combo item = id + text
    // ---------------------------------------------------------------
    public static class Item {
        public final int id;
        public final String label;

        public Item(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ---------------------------------------------------------------
    // Database helpers
    // ---------------------------------------------------------------
    public static void ensureTables(Connection con) {

        createIfMissing(con, "COUNSELLING_SESSIONS",
            "CREATE TABLE COUNSELLING_SESSIONS ("
            + "SESSION_ID NUMBER PRIMARY KEY, "
            + "PRISONER_ID NUMBER NOT NULL, "
            + "COUNSELLOR_ID NUMBER NOT NULL, "
            + "SESSION_DATE DATE NOT NULL, "
            + "SESSION_MODE VARCHAR2(30), "
            + "TOPIC VARCHAR2(200 CHAR), "
            + "STATUS VARCHAR2(20) DEFAULT 'Scheduled', "
            + "NOTES CLOB, "
            + "PROGRESS_RATING NUMBER(1), "
            + "FOLLOW_UP VARCHAR2(500 CHAR), "
            + "CREATED_DATE DATE DEFAULT SYSDATE)");

        createIfMissing(con, "REHABILITATION_PLANS",
            "CREATE TABLE REHABILITATION_PLANS ("
            + "PLAN_ID NUMBER PRIMARY KEY, "
            + "PRISONER_ID NUMBER NOT NULL, "
            + "COUNSELLOR_ID NUMBER, "
            + "CATEGORY VARCHAR2(40), "
            + "GOAL VARCHAR2(300 CHAR) NOT NULL, "
            + "TARGET_DATE DATE, "
            + "STATUS VARCHAR2(20) DEFAULT 'Not Started', "
            + "REMARKS VARCHAR2(500 CHAR), "
            + "LAST_UPDATED DATE DEFAULT SYSDATE)");

        // REbirth Compass tables and columns
        CompassEngine.ensure(con);
    }

    private static void createIfMissing(Connection con, String table, String ddl) {

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT 1 FROM " + table + " WHERE 1=0")) {
            return;
        } catch (SQLException missing) {
            // table does not exist, create it below
        }

        try (Statement st = con.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ignored) {
            // another window may have created it at the same time
        }
    }

    public static int counsellorId(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNSELLOR_ID FROM COUNSELLOR_DETAILS WHERE USER_ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Counsellor profile not found for this user.");
    }

    public static int prisonerId(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT PRISONER_ID FROM PRISONER_DETAILS WHERE USER_ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Prisoner details not found for this user.");
    }

    public static List<Item> prisoners(Connection con) throws SQLException {
        List<Item> list = new ArrayList<>();
        String sql = "SELECT p.PRISONER_ID, u.FULL_NAME FROM PRISONER_DETAILS p "
                   + "JOIN USERS u ON u.USER_ID = p.USER_ID ORDER BY u.FULL_NAME";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                list.add(new Item(id, rs.getString(2) + "  (ID " + id + ")"));
            }
        }
        return list;
    }

    /** Only call with fixed table/column names (never user text). */
    public static int nextId(Connection con, String table, String column)
            throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT NVL(MAX(" + column + "), 0) + 1 FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static String fmt(Timestamp t, String pattern) {
        return t == null ? "" : new SimpleDateFormat(pattern).format(t);
    }

    // ---------------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------------
    public static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable table(DefaultTableModel m) {

        JTable t = new JTable(m);
        t.setRowHeight(30);
        t.setFont(Theme.f(Font.PLAIN, 13));
        t.setForeground(Theme.TEXT);
        t.setGridColor(Theme.BORDER);
        t.setSelectionBackground(Theme.GREEN_SOFT);
        t.setSelectionForeground(Theme.TEXT);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);

        DefaultTableCellRenderer zebra = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb, v, sel, false, r, c);
                setBackground(sel ? Theme.GREEN_SOFT
                    : (r % 2 == 0 ? Color.WHITE : new Color(244, 250, 246)));
                setForeground(Theme.TEXT);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, zebra);
        t.setDefaultRenderer(Integer.class, zebra);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));

        DefaultTableCellRenderer head = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb, v, sel, foc, r, c);
                setBackground(Theme.GREEN);
                setForeground(Color.WHITE);
                setFont(Theme.f(Font.BOLD, 13));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return this;
            }
        };
        t.getTableHeader().setDefaultRenderer(head);
        t.getTableHeader().setPreferredSize(new Dimension(100, 34));
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    public static JScrollPane scroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        Theme.slim(sp);
        return sp;
    }

    public static JComboBox<String> combo(String... items) {
        Theme.install();
        JComboBox<String> c = Theme.flat(new JComboBox<String>(items));
        c.setFont(Theme.f(Font.PLAIN, 14));
        c.setPreferredSize(new Dimension(200, 36));
        return c;
    }

    public static JSpinner dateSpinner(java.util.Date initial, String pattern) {
        JSpinner s = new JSpinner(new SpinnerDateModel(
            initial, null, null, java.util.Calendar.MINUTE));
        s.setEditor(new JSpinner.DateEditor(s, pattern));
        s.setFont(Theme.f(Font.PLAIN, 14));
        s.setPreferredSize(new Dimension(200, 36));
        return s;
    }

    /** White rounded card with a GridBag layout, for the input forms. */
    public static JPanel formCard() {
        Theme.Card card = new Theme.Card(Color.WHITE);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 20, 18));
        return card;
    }

    /** Adds "Label  [component]" as one row of a formCard(). */
    public static void addRow(JPanel p, int row, String label, JComponent comp) {

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.anchor = GridBagConstraints.NORTHWEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = row;

        JLabel l = new JLabel(label);
        l.setFont(Theme.f(Font.BOLD, 13));
        l.setForeground(Theme.TEXT);

        g.gridx = 0;
        g.weightx = 0;
        p.add(l, g);

        g.gridx = 1;
        g.weightx = 1;
        p.add(comp, g);
    }

    /** Like addRow, but the component takes all spare height (for notes boxes). */
    public static void addTallRow(JPanel p, int row, String label, JComponent comp) {

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.anchor = GridBagConstraints.NORTHWEST;
        g.gridy = row;

        JLabel l = new JLabel(label);
        l.setFont(Theme.f(Font.BOLD, 13));
        l.setForeground(Theme.TEXT);

        g.gridx = 0;
        g.weightx = 0;
        g.weighty = 0;
        g.fill = GridBagConstraints.NONE;
        p.add(l, g);

        g.gridx = 1;
        g.weightx = 1;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        p.add(comp, g);
    }

    public static JPanel buttonRow(JComponent... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        for (JComponent b : buttons) {
            row.add(b);
        }
        return row;
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "REbirth",
            JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "REbirth",
            JOptionPane.INFORMATION_MESSAGE);
    }
}