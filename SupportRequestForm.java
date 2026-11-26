package rebirth;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Support requests.
 *
 *   Prisoner                 : raise a request, follow its progress (stepper).
 *   NGO / Counsellor / Admin : see every request (oldest open one first),
 *                              change the status and write the resolution.
 *
 * New in this version
 *   - Same look as the other screens (Theme) + Back to Dashboard button
 *   - Progress stepper:  Pending  ->  In Progress  ->  Resolved
 *   - "Waiting" column: how many days an open request has been waiting
 *     (turns red after 7 days so nobody is forgotten)
 */
public class SupportRequestForm extends JFrame {

    private static final String[] STEPS = {"Pending", "In Progress", "Resolved"};

    private final int userId;
    private final boolean prisoner;
    private final boolean manager;
    private int ownPrisonerId;

    private final JComboBox<String> requestType = CounsellorUtil.combo(
        "Counselling", "Employment Assistance", "Education and Training",
        "Legal Assistance", "Healthcare", "Family Reintegration", "Other");
    private final JComboBox<String> status =
        CounsellorUtil.combo("Pending", "In Progress", "Resolved");
    private final JTextArea description = Theme.area(3);
    private final JTextArea resolution = Theme.area(2);

    private final Stepper stepper = new Stepper();
    private final DefaultTableModel model;
    private final JTable table;

    public SupportRequestForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.prisoner = role.equalsIgnoreCase("Prisoner");
        this.manager = role.equalsIgnoreCase("NGO")
                || role.equalsIgnoreCase("Counsellor")
                || role.equalsIgnoreCase("Admin")
                || role.equalsIgnoreCase("Administrator");

        setTitle("REbirth - Support Requests");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        model = CounsellorUtil.readOnlyModel("ID", "Prisoner", "Type",
            "Description", "Date", "Status", "Resolution", "Waiting");
        table = CounsellorUtil.table(model);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(300);
        table.getColumnModel().getColumn(4).setPreferredWidth(95);
        table.getColumnModel().getColumn(5).setPreferredWidth(95);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new StatusRenderer());

        if (prisoner) {
            try (Connection con = DBConnection.getConnection()) {
                ownPrisonerId = CounsellorUtil.prisonerId(con, userId);
            } catch (SQLException ex) {
                CounsellorUtil.error(this, ex.getMessage());
            }
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("Support Requests",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(buildForm(), BorderLayout.CENTER);
        top.add(stepper, BorderLayout.SOUTH);

        body.add(top, BorderLayout.NORTH);
        body.add(CounsellorUtil.scroll(table), BorderLayout.CENTER);
        body.add(buildButtons(), BorderLayout.SOUTH);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onRowSelected();
            }
        });

        loadRequests();
    }

    // ------------------------------------------------------------------
    private JPanel buildForm() {

        JPanel card = CounsellorUtil.formCard();

        if (prisoner) {
            CounsellorUtil.addRow(card, 0, "Request Type", requestType);
            CounsellorUtil.addRow(card, 1, "Description", Theme.scroll(description));
        } else {
            description.setEditable(false);
            description.setBackground(new Color(246, 248, 246));
            CounsellorUtil.addRow(card, 0, "Request", Theme.scroll(description));
            CounsellorUtil.addRow(card, 1, "Status", status);
            CounsellorUtil.addRow(card, 2, "Resolution", Theme.scroll(resolution));
        }
        return card;
    }

    private JPanel buildButtons() {

        Theme.Button refresh = new Theme.Button("Refresh",
            new Color(120, 130, 150), Color.WHITE);
        Theme.Button clear = new Theme.Button("Clear",
            new Color(180, 120, 145), Color.WHITE);
        refresh.addActionListener(e -> loadRequests());
        clear.addActionListener(e -> clearForm());

        if (prisoner) {
            Theme.Button submit = new Theme.Button("Submit Request",
                Theme.GREEN, Color.WHITE);
            submit.addActionListener(e -> submitRequest());
            return CounsellorUtil.buttonRow(submit, clear, refresh);
        }
        if (manager) {
            Theme.Button update = new Theme.Button("Update Request",
                Theme.GREEN_DARK, Color.WHITE);
            update.addActionListener(e -> updateRequest());
            return CounsellorUtil.buttonRow(update, clear, refresh);
        }
        return CounsellorUtil.buttonRow(refresh);
    }

    // ------------------------------------------------------------------
    private void onRowSelected() {

        int row = table.getSelectedRow();
        if (row < 0) {
            stepper.setStatus(null);
            return;
        }
        String st = String.valueOf(model.getValueAt(row, 5));
        stepper.setStatus(st);

        if (manager) {
            description.setText(String.valueOf(model.getValueAt(row, 3)));
            description.setCaretPosition(0);
            status.setSelectedItem(st);
            Object res = model.getValueAt(row, 6);
            resolution.setText(res == null ? "" : res.toString());
        }
    }

    private void clearForm() {
        table.clearSelection();
        stepper.setStatus(null);
        resolution.setText("");
        description.setText("");
        if (prisoner) {
            requestType.setSelectedIndex(0);
        } else {
            status.setSelectedIndex(0);
        }
    }

    // ------------------------------------------------------------------
    private void loadRequests() {

        model.setRowCount(0);

        String sql = "SELECT r.REQUEST_ID, u.FULL_NAME, r.REQUEST_TYPE, "
            + "r.DESCRIPTION, r.REQUEST_DATE, r.STATUS, r.RESOLUTION_DETAILS, "
            + "TRUNC(SYSDATE) - TRUNC(r.REQUEST_DATE) "
            + "FROM SUPPORT_REQUESTS r "
            + "JOIN PRISONER_DETAILS p ON p.PRISONER_ID = r.PRISONER_ID "
            + "JOIN USERS u ON u.USER_ID = p.USER_ID ";

        if (prisoner) {
            sql += "WHERE r.PRISONER_ID = ? ORDER BY r.REQUEST_ID DESC";
        } else {
            // open requests first, oldest first: the longest wait is on top
            sql += "ORDER BY CASE WHEN UPPER(r.STATUS) = 'RESOLVED' THEN 1 ELSE 0 END, "
                 + "r.REQUEST_ID";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (prisoner) {
                ps.setInt(1, ownPrisonerId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String st = Theme.nz(rs.getString(6));
                    int days = rs.getInt(8);
                    boolean open = !st.equalsIgnoreCase("Resolved");
                    model.addRow(new Object[]{
                        rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getDate(5), st,
                        Theme.nz(rs.getString(7)),
                        open ? days + (days == 1 ? " day" : " days") : "-"
                    });
                }
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
        stepper.setStatus(null);
    }

    private void submitRequest() {

        String text = description.getText().trim();
        if (text.isEmpty()) {
            CounsellorUtil.error(this, "Enter a request description.");
            return;
        }
        if (ownPrisonerId == 0) {
            CounsellorUtil.error(this, "Prisoner details not found.");
            return;
        }

        String sql = "INSERT INTO SUPPORT_REQUESTS "
            + "(REQUEST_ID, PRISONER_ID, REQUEST_TYPE, DESCRIPTION, "
            + "REQUEST_DATE, STATUS) "
            + "VALUES ((SELECT NVL(MAX(REQUEST_ID), 0) + 1 FROM SUPPORT_REQUESTS), "
            + "?, ?, ?, SYSDATE, 'Pending')";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, ownPrisonerId);
            ps.setString(2, requestType.getSelectedItem().toString());
            ps.setString(3, text);
            ps.executeUpdate();

            CounsellorUtil.info(this, "Support request submitted.");
            description.setText("");
            loadRequests();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void updateRequest() {

        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a request from the table.");
            return;
        }

        String newStatus = status.getSelectedItem().toString();
        String res = resolution.getText().trim();

        if (newStatus.equals("Resolved") && res.isEmpty()) {
            CounsellorUtil.error(this,
                "Enter resolution details before marking it Resolved.");
            return;
        }

        int id = (Integer) model.getValueAt(row, 0);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE SUPPORT_REQUESTS SET STATUS = ?, RESOLUTION_DETAILS = ? "
                 + "WHERE REQUEST_ID = ?")) {

            ps.setString(1, newStatus);
            ps.setString(2, res);
            ps.setInt(3, id);
            ps.executeUpdate();

            CounsellorUtil.info(this, "Request updated.");
            loadRequests();

        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    /** Colours the Status and Waiting columns. */
    private static class StatusRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {

            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setFont(Theme.f(Font.BOLD, 13));
            setForeground(Theme.TEXT);

            String text = String.valueOf(v);
            if (c == 5) {
                if (text.equalsIgnoreCase("Resolved")) {
                    setForeground(Theme.GREEN);
                } else if (text.equalsIgnoreCase("In Progress")) {
                    setForeground(Theme.ORANGE);
                } else {
                    setForeground(Theme.RED);
                }
            } else if (c == 7 && text.endsWith("days")) {
                try {
                    int days = Integer.parseInt(text.split(" ")[0]);
                    if (days > 7) {
                        setForeground(Theme.RED);
                    }
                } catch (NumberFormatException ignored) {
                    // keep the default colour
                }
            }
            return this;
        }
    }

    /** Pending -> In Progress -> Resolved, drawn for the selected request. */
    private static class Stepper extends JComponent {

        private int current = -1;

        Stepper() {
            setPreferredSize(new Dimension(100, 62));
        }

        void setStatus(String s) {
            current = -1;
            if (s != null) {
                for (int i = 0; i < STEPS.length; i++) {
                    if (STEPS[i].equalsIgnoreCase(s.trim())) {
                        current = i;
                    }
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);

            int w = getWidth();
            int cy = 20;
            int r = 14;
            int margin = 90;
            int usable = Math.max(1, w - 2 * margin);

            if (current < 0) {
                g2.setFont(Theme.f(Font.ITALIC, 13));
                g2.setColor(Theme.MUTED);
                g2.drawString("Select a request to see its progress.", 4, 32);
                g2.dispose();
                return;
            }

            for (int i = 0; i < STEPS.length; i++) {
                int cx = margin + usable * i / (STEPS.length - 1);

                if (i < STEPS.length - 1) {
                    int nx = margin + usable * (i + 1) / (STEPS.length - 1);
                    g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, 0));
                    g2.setColor(i < current ? Theme.GREEN : new Color(214, 222, 217));
                    g2.drawLine(cx + r + 4, cy, nx - r - 4, cy);
                }

                boolean reached = i <= current;
                g2.setColor(reached ? Theme.GREEN : Color.WHITE);
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g2.setColor(reached ? Theme.GREEN : Theme.BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));

                g2.setColor(reached ? Color.WHITE : Theme.MUTED);
                g2.setFont(Theme.f(Font.BOLD, 13));
                String n = i < current ? "\u2713" : String.valueOf(i + 1);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(n, cx - fm.stringWidth(n) / 2,
                    cy + fm.getAscent() / 2 - 1);

                g2.setFont(Theme.f(i == current ? Font.BOLD : Font.PLAIN, 12));
                g2.setColor(i == current ? Theme.GREEN_DARK : Theme.MUTED);
                fm = g2.getFontMetrics();
                g2.drawString(STEPS[i], cx - fm.stringWidth(STEPS[i]) / 2,
                    cy + r + 17);
            }
            g2.dispose();
        }
    }
}
