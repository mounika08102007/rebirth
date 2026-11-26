package rebirth;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import java.util.List;
import javax.swing.*;

/**
 * Counsellor: "who do I call today?"
 *
 * Plain IF-rules (no machine learning). Every card lists the exact reasons
 * and the points each one added, so a flag can always be explained.
 */
public class EarlyWarningForm extends JFrame {

    private final int userId;
    private final String fullName;
    private final String role;

    private final JComboBox<String> window = CounsellorUtil.combo(
        "First 90 days after release", "Everyone released");
    private final JPanel kpis = new JPanel(new GridLayout(1, 4, 14, 0));
    private final JPanel list = new JPanel();
    private final JLabel status = new JLabel(" ");

    public EarlyWarningForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;

        setTitle("REbirth - Early Warning");
        setSize(1120, 860);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("Early Warning",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        kpis.setOpaque(false);
        kpis.setPreferredSize(new Dimension(100, 112));

        window.setPreferredSize(new Dimension(240, 36));
        Theme.Button refresh = new Theme.Button("Refresh",
            new Color(120, 130, 150), Color.WHITE);
        Theme.Button how = new Theme.Button("How flags work",
            Theme.GREEN_DARK, Color.WHITE);
        refresh.addActionListener(e -> load());
        how.addActionListener(e -> explain());
        window.addActionListener(e -> load());

        status.setFont(Theme.f(Font.PLAIN, 12));
        status.setForeground(Theme.MUTED);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bar.setOpaque(false);
        JLabel wl = new JLabel("Watch window:");
        wl.setFont(Theme.f(Font.BOLD, 13));
        bar.add(wl);
        bar.add(window);
        bar.add(refresh);
        bar.add(how);
        bar.add(status);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(kpis, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        Theme.slim(sp);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(22);
        body.add(sp, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        load();
    }

    // ------------------------------------------------------------------
    private void load() {

        status.setText("Checking everyone...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        boolean first90 = window.getSelectedIndex() == 0;

        new SwingWorker<List<CompassEngine.Alert>, Void>() {
            @Override
            protected List<CompassEngine.Alert> doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    CompassEngine.ensure(con);
                    return CompassEngine.earlyWarningAll(con, first90);
                }
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    show(get());
                    status.setText(" ");
                } catch (Exception ex) {
                    status.setText(" ");
                    CounsellorUtil.error(EarlyWarningForm.this,
                        String.valueOf(ex.getCause() != null
                            ? ex.getCause().getMessage() : ex.getMessage()));
                }
            }
        }.execute();
    }

    private void show(List<CompassEngine.Alert> all) {

        int high = 0;
        int med = 0;
        int watch = 0;
        for (CompassEngine.Alert a : all) {
            if (a.level.equals("HIGH")) {
                high++;
            } else if (a.level.equals("MEDIUM")) {
                med++;
            } else if (a.level.equals("WATCH")) {
                watch++;
            }
        }

        kpis.removeAll();
        kpis.add(kpi("Call today", high, new Color(255, 96, 96), "5+ points"));
        kpis.add(kpi("Call this week", med, new Color(255, 170, 70), "3-4 points"));
        kpis.add(kpi("Keep watching", watch, new Color(240, 212, 80), "1-2 points"));
        kpis.add(kpi("People checked", all.size(), CompassVisuals.D_NEON, "in this window"));
        kpis.revalidate();
        kpis.repaint();

        list.removeAll();
        int shown = 0;
        for (CompassEngine.Alert a : all) {
            if (a.points > 0) {
                list.add(new AlertCard(a));
                list.add(Box.createVerticalStrut(12));
                shown++;
            }
        }
        if (shown == 0) {
            JLabel none = new JLabel(all.isEmpty()
                ? "Nobody is inside this watch window. Try \"Everyone released\"."
                : "No warnings right now. Everyone in this window looks steady.");
            none.setFont(Theme.f(Font.BOLD, 15));
            none.setForeground(Theme.GREEN_DARK);
            none.setBorder(BorderFactory.createEmptyBorder(30, 8, 0, 0));
            list.add(none);
        }
        list.revalidate();
        list.repaint();
    }

    private static CompassVisuals.KpiCard kpi(String title, int v, Color c, String sub) {
        CompassVisuals.KpiCard k = new CompassVisuals.KpiCard(title, c);
        k.setValue(v, "", sub);
        return k;
    }

    private void explain() {
        JOptionPane.showMessageDialog(this,
            "Every flag is a plain rule, so you can always say why someone was flagged.\n\n"
            + "Support request still Pending:  3+ days = 1 point,  7+ days = 3 points\n"
            + "No completed counselling session:  15+ days = 1,  31+ days = 2\n"
            + "Rehabilitation goals past target date:  1-2 = 1,  3 or more = 2\n"
            + "Readiness index fell 5+ points since last week:  2\n"
            + "One pillar fell 10+ points:  1\n"
            + "Last session rated 1 or 2 out of 5:  1\n"
            + "No job or income more than 30 days after release:  1\n"
            + "Readiness index below 35:  1\n\n"
            + "Total  5+ = HIGH (call today),  3-4 = MEDIUM,  1-2 = WATCH.\n\n"
            + "This is decision support, not a prediction of who will reoffend.\n"
            + "The points are a starting point that experts would adjust.",
            "How flags work", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------
    /** One person: colour strip, index disc, reasons, buttons. */
    private class AlertCard extends JPanel {

        private final Color col;
        private boolean hover;

        AlertCard(CompassEngine.Alert a) {

            CompassEngine.Compass c = a.compass;
            col = a.level.equals("HIGH") ? Theme.RED
                : a.level.equals("MEDIUM") ? Theme.ORANGE : new Color(176, 140, 20);

            setOpaque(false);
            setLayout(new BorderLayout(16, 0));
            setBorder(BorderFactory.createEmptyBorder(14, 22, 16, 16));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            add(new Disc(c.index, col, a.level.equals("HIGH")), BorderLayout.WEST);

            JPanel mid = new JPanel();
            mid.setOpaque(false);
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));

            JLabel name = new JLabel(c.name + "   ");
            name.setFont(Theme.f(Font.BOLD, 17));
            name.setForeground(Theme.TEXT);
            Theme.Badge lvl = new Theme.Badge(
                a.level.equals("HIGH") ? "CALL TODAY" : a.level.equals("MEDIUM")
                    ? "CALL THIS WEEK" : "WATCH",
                CompassVisuals.alpha(col, 40), col);
            JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            nameRow.setOpaque(false);
            nameRow.add(name);
            nameRow.add(lvl);
            nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            mid.add(nameRow);

            String sub = a.points + " warning points";
            if (c.release != null && c.daysSinceRelease >= 0) {
                sub += "   \u00B7   day " + c.daysSinceRelease + " since release";
            }
            if (!c.district.isEmpty()) {
                sub += "   \u00B7   " + c.district;
            }
            sub += "   \u00B7   weakest: " + CompassEngine.PILLARS[c.weakest()];
            mid.add(Theme.text(sub, 560, 12, Theme.MUTED));
            mid.add(Box.createVerticalStrut(6));

            for (String r : a.reasons) {
                mid.add(Theme.text("\u2022  " + r, 600, 13, Theme.TEXT));
                mid.add(Box.createVerticalStrut(2));
            }
            mid.add(Box.createVerticalStrut(6));
            JLabel sug = Theme.text(a.suggestion, 600, 13, Theme.GREEN_DARK);
            sug.setFont(Theme.f(Font.BOLD, 13));
            mid.add(sug);
            add(mid, BorderLayout.CENTER);

            JPanel btns = new JPanel();
            btns.setOpaque(false);
            btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));
            Theme.Button open = new Theme.Button("Open Compass", Theme.GREEN, Color.WHITE);
            Theme.Button sess = new Theme.Button("Schedule Session",
                Theme.GREEN_DARK, Color.WHITE);
            open.addActionListener(e ->
                new CompassForm(userId, fullName, role, c.prisonerId).setVisible(true));
            sess.addActionListener(e -> RoleBasedDashboard.open("Schedule Session"));
            btns.add(open);
            btns.add(Box.createVerticalStrut(8));
            btns.add(sess);
            add(btns, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g.setColor(new Color(0, 0, 0, hover ? 38 : 20));
            g.fill(new RoundRectangle2D.Double(1, 3, w - 3, h - 6, 18, 18));
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 6, 18, 18));
            g.setColor(hover ? col : Theme.BORDER);
            g.setStroke(new BasicStroke(hover ? 1.8f : 1f));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 6, 18, 18));
            g.setColor(col);
            g.fill(new RoundRectangle2D.Double(1, 1, 8, h - 6, 8, 8));
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Round disc with the readiness index; HIGH pulses. */
    private static class Disc extends CompassVisuals.AnimPanel {

        private final double idx;
        private final Color col;
        private final boolean pulse;

        Disc(double idx, Color col, boolean pulse) {
            this.idx = idx;
            this.col = col;
            this.pulse = pulse;
            continuous = pulse;
            durationMs = 900;
            setPreferredSize(new Dimension(78, 78));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.quality(g);
            double cx = getWidth() / 2.0;
            double cy = 38;
            if (pulse) {
                double p = 0.5 + 0.5 * Math.sin(clock * 4);
                double r = 30 + 6 * p;
                g.setColor(CompassVisuals.alpha(col, (int) (70 - 40 * p)));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
            g.setColor(CompassVisuals.alpha(col, 40));
            g.fill(new Ellipse2D.Double(cx - 28, cy - 28, 56, 56));
            g.setColor(col);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Arc2D.Double(cx - 28, cy - 28, 56, 56, 90,
                -360 * idx / 100.0 * CompassVisuals.easeOut(t),
                java.awt.geom.Arc2D.OPEN));
            g.setFont(Theme.f(Font.BOLD, 20));
            g.setColor(col.darker());
            CompassVisuals.centered(g, String.valueOf(Math.round(idx)), cx, cy + 7);
            g.setFont(Theme.f(Font.PLAIN, 9));
            g.setColor(Theme.MUTED);
            CompassVisuals.centered(g, "INDEX", cx, cy + 36);
            g.dispose();
        }
    }
}