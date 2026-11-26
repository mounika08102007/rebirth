package rebirth;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Readiness Compass for one person.
 *
 * Prisoner   : sees their own compass, tree, pillar details and matched schemes.
 * Counsellor : picks any prisoner, also sees the early-warning reasons and can
 *              open the checklist (documents, housing, family, legal).
 *
 * Click a pillar on the radar to "zoom" into how that score was made.
 */
public class CompassForm extends JFrame {

    private final int userId;
    private final String fullName;
    private final String role;
    private final boolean counsellor;
    private int ownPrisonerId;
    private final int initialPrisoner;

    private final JComboBox<CounsellorUtil.Item> personBox =
        Theme.flat(new JComboBox<CounsellorUtil.Item>());
    private final CompassVisuals.RadarChart radar = new CompassVisuals.RadarChart();
    private final CompassVisuals.RingGauge ring = new CompassVisuals.RingGauge();
    private final CompassVisuals.GrowthTree tree = new CompassVisuals.GrowthTree();

    private final JPanel summary = vbox();
    private final JPanel detail = new JPanel(new BorderLayout());
    private final JPanel schemes = vbox();

    private CompassEngine.Compass current;
    private List<CompassEngine.SchemeInfo> allSchemes = new ArrayList<>();
    private int shownPillar = -1;

    public CompassForm(int userId, String fullName, String role) {
        this(userId, fullName, role, 0);
    }

    public CompassForm(int userId, String fullName, String role, int prisonerId) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.counsellor = role.equalsIgnoreCase("Counsellor");
        this.initialPrisoner = prisonerId;

        setTitle(counsellor ? "REbirth - Readiness Compass" : "REbirth - My Compass");
        setSize(1380, 920);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.ensure(con);
            CounsellorUtil.ensureTables(con);
            if (counsellor) {
                for (CounsellorUtil.Item i : CounsellorUtil.prisoners(con)) {
                    personBox.addItem(i);
                    if (i.id == initialPrisoner) {
                        personBox.setSelectedItem(i);
                    }
                }
            } else {
                ownPrisonerId = CounsellorUtil.prisonerId(con, userId);
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand(
            counsellor ? "Readiness Compass" : "My Readiness Compass",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 18, 24));
        body.add(buildToolbar(), BorderLayout.NORTH);
        body.add(buildMain(), BorderLayout.CENTER);

        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null);
        Theme.slim(sp);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        root.add(sp, BorderLayout.CENTER);
        setContentPane(root);

        radar.setOnSelect(this::showPillar);
        if (counsellor) {
            personBox.addActionListener(e -> load());
        }
        load();
    }

    // ------------------------------------------------------------------
    private static JPanel vbox() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private JPanel buildToolbar() {

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bar.setOpaque(false);

        if (counsellor) {
            JLabel l = new JLabel("Person:");
            l.setFont(Theme.f(Font.BOLD, 13));
            personBox.setFont(Theme.f(Font.PLAIN, 14));
            personBox.setPreferredSize(new Dimension(320, 36));
            bar.add(l);
            bar.add(personBox);

            Theme.Button edit = new Theme.Button("Edit Checklist",
                Theme.GREEN_DARK, Color.WHITE);
            edit.addActionListener(e -> {
                int pid = currentPid();
                if (pid != 0) {
                    new ChecklistForm(userId, fullName, role, pid, this::load)
                        .setVisible(true);
                }
            });
            Theme.Button warn = new Theme.Button("Early Warning List",
                Theme.ORANGE, Color.WHITE);
            warn.addActionListener(e ->
                new EarlyWarningForm(userId, fullName, role).setVisible(true));
            bar.add(edit);
            bar.add(warn);
        }

        Theme.Button refresh = new Theme.Button("Refresh",
            new Color(120, 130, 150), Color.WHITE);
        refresh.addActionListener(e -> load());
        bar.add(refresh);
        return bar;
    }

    private JPanel buildMain() {

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 16, 16);

        // radar
        Theme.Card radarCard = new Theme.Card(Color.WHITE);
        radarCard.setLayout(new BorderLayout());
        radarCard.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        radarCard.add(title("Compass", "Click any pillar to see how its score was made"),
            BorderLayout.NORTH);
        radarCard.add(radar, BorderLayout.CENTER);
        g.gridx = 0; g.gridy = 0; g.weightx = 0.46; g.weighty = 0.6;
        grid.add(radarCard, g);

        // ring + summary
        Theme.Card mid = new Theme.Card(Color.WHITE);
        mid.setLayout(new BorderLayout(0, 6));
        mid.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        mid.add(ring, BorderLayout.NORTH);
        mid.add(summary, BorderLayout.CENTER);
        g.gridx = 1; g.weightx = 0.28;
        grid.add(mid, g);

        // tree
        Theme.Card treeCard = new Theme.Card(Color.WHITE);
        treeCard.setLayout(new BorderLayout());
        treeCard.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        treeCard.add(title("Rebirth Tree", "Grows with the index"), BorderLayout.NORTH);
        treeCard.add(tree, BorderLayout.CENTER);
        g.gridx = 2; g.weightx = 0.26; g.insets = new Insets(0, 0, 16, 0);
        grid.add(treeCard, g);

        // detail
        Theme.Card detailCard = new Theme.Card(Color.WHITE);
        detailCard.setLayout(new BorderLayout());
        detailCard.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        detail.setOpaque(false);
        detailCard.add(detail, BorderLayout.CENTER);
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2; g.weightx = 0.74; g.weighty = 0.4;
        g.insets = new Insets(0, 0, 0, 16);
        grid.add(detailCard, g);

        // schemes
        Theme.Card schemeCard = new Theme.Card(Color.WHITE);
        schemeCard.setLayout(new BorderLayout());
        schemeCard.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        schemeCard.add(title("Scheme Matcher", "The right schemes, not a long list"),
            BorderLayout.NORTH);
        schemeCard.add(schemes, BorderLayout.CENTER);
        g.gridx = 2; g.gridwidth = 1; g.weightx = 0.26; g.insets = new Insets(0, 0, 0, 0);
        grid.add(schemeCard, g);

        return grid;
    }

    private static JPanel title(String t, String sub) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel a = new JLabel(t);
        a.setFont(Theme.f(Font.BOLD, 17));
        a.setForeground(Theme.TEXT);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel b = new JLabel(sub);
        b.setFont(Theme.f(Font.PLAIN, 12));
        b.setForeground(Theme.MUTED);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(a);
        p.add(b);
        p.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 0));
        return p;
    }

    // ------------------------------------------------------------------
    private int currentPid() {
        if (!counsellor) {
            return ownPrisonerId;
        }
        CounsellorUtil.Item it = (CounsellorUtil.Item) personBox.getSelectedItem();
        return it == null ? 0 : it.id;
    }

    private void load() {

        int pid = currentPid();
        if (pid == 0) {
            summary.removeAll();
            summary.add(Theme.text("No prisoner selected.", 240, 13, Theme.MUTED));
            summary.revalidate();
            summary.repaint();
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            current = CompassEngine.compute(con, pid);
            CompassEngine.snapshot(con, current);
            allSchemes = CompassEngine.activeSchemes(con);
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
            return;
        }

        int weak = current.weakest();
        radar.setData(current.score, current.prev, weak);
        ring.setValue(current.index, CompassEngine.BANDS[CompassEngine.band(current.index)]);
        tree.setData(current.index, current.score);
        buildSummary();
        buildSchemes();
        showPillar(weak);
        radar.setSelected(weak);
    }

    // ------------------------------------------------------------------
    private void buildSummary() {

        CompassEngine.Compass c = current;
        summary.removeAll();

        JLabel name = new JLabel(counsellor ? c.name : "Hello, " + firstName(c.name));
        name.setFont(Theme.f(Font.BOLD, 20));
        name.setForeground(Theme.TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(name);

        String sub = "";
        if (c.release != null && c.daysSinceRelease >= 0) {
            sub = "Day " + c.daysSinceRelease + " since release";
        }
        if (!c.district.isEmpty()) {
            sub += (sub.isEmpty() ? "" : "   \u00B7   ") + c.district;
        }
        if (!sub.isEmpty()) {
            summary.add(Theme.text(sub, 250, 12, Theme.MUTED));
        }
        summary.add(Box.createVerticalStrut(10));

        int w = c.weakest();
        JLabel weak = new JLabel("Weakest pillar: " + CompassEngine.PILLARS[w]
            + "  (" + Math.round(c.score[w]) + ")");
        weak.setFont(Theme.f(Font.BOLD, 13));
        weak.setForeground(Theme.RED);
        weak.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(weak);
        summary.add(Box.createVerticalStrut(4));

        String trend;
        Color tc = Theme.MUTED;
        if (c.prev == null) {
            trend = "Trend appears after a week of history.";
        } else {
            double d = c.index - c.prevIndex;
            if (d >= 1) {
                trend = "\u25B2 +" + Math.round(d) + " since " + c.prevDate;
                tc = Theme.GREEN;
            } else if (d <= -1) {
                trend = "\u25BC " + Math.round(d) + " since " + c.prevDate;
                tc = Theme.RED;
            } else {
                trend = "\u25CF Steady since " + c.prevDate;
            }
        }
        JLabel tl = new JLabel(trend);
        tl.setFont(Theme.f(Font.BOLD, 12));
        tl.setForeground(tc);
        tl.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(tl);

        List<String> notAssessed = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (!c.assessed[i]) {
                notAssessed.add(CompassEngine.SHORT[i]);
            }
        }
        if (!notAssessed.isEmpty()) {
            summary.add(Box.createVerticalStrut(8));
            summary.add(Theme.text("Not yet assessed: " + String.join(", ", notAssessed)
                + ". These count as 0 until the checklist is filled.", 250, 11, Theme.ORANGE));
        }

        if (counsellor) {
            CompassEngine.Alert a = CompassEngine.evaluate(c);
            summary.add(Box.createVerticalStrut(12));
            Color col = a.level.equals("HIGH") ? Theme.RED
                : a.level.equals("MEDIUM") ? Theme.ORANGE
                : a.level.equals("WATCH") ? new Color(160, 120, 20) : Theme.GREEN;
            Theme.Badge b = new Theme.Badge(
                a.level.equals("OK") ? "NO WARNINGS" : "EARLY WARNING: " + a.level,
                CompassVisuals.alpha(col, 40), col);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            summary.add(b);
            for (String r : a.reasons) {
                summary.add(Box.createVerticalStrut(3));
                summary.add(Theme.text("\u2022 " + r, 250, 11, Theme.TEXT));
            }
        }
        summary.revalidate();
        summary.repaint();
    }

    private static String firstName(String n) {
        String t = n.trim();
        int sp = t.indexOf(' ');
        return sp > 0 ? t.substring(0, sp) : t;
    }

    // ------------------------------------------------------------------
    // Pillar zoom
    // ------------------------------------------------------------------
    private void showPillar(int i) {

        if (current == null || i < 0) {
            return;
        }
        shownPillar = i;
        Color col = CompassVisuals.PILLAR_COLORS[i];

        JPanel box = new JPanel(new BorderLayout(0, 10));
        box.setOpaque(false);

        // header
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel left = vbox();
        JLabel t = new JLabel(CompassEngine.PILLARS[i] + "  \u00B7  how this score was made");
        t.setFont(Theme.f(Font.BOLD, 17));
        t.setForeground(col);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(t);
        JLabel s = new JLabel("Weight in the index: " + CompassEngine.WEIGHTS[i]
            + "%   \u00B7   adds " + String.format("%.1f",
                current.score[i] * CompassEngine.WEIGHTS[i] / 100.0)
            + " of " + CompassEngine.WEIGHTS[i] + " points");
        s.setFont(Theme.f(Font.PLAIN, 12));
        s.setForeground(Theme.MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(s);
        head.add(left, BorderLayout.WEST);

        JLabel big = new JLabel(String.valueOf(Math.round(current.score[i])) + " / 100");
        big.setFont(Theme.f(Font.BOLD, 26));
        big.setForeground(col);
        head.add(big, BorderLayout.EAST);
        box.add(head, BorderLayout.NORTH);

        // rows
        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 10);
        g.anchor = GridBagConstraints.WEST;
        int r = 0;
        for (CompassEngine.Line l : current.lines.get(i)) {
            g.gridy = r++;

            JLabel lab = new JLabel(l.label);
            lab.setFont(Theme.f(Font.BOLD, 13));
            lab.setForeground(Theme.TEXT);
            g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            lab.setPreferredSize(new Dimension(210, 22));
            rows.add(lab, g);

            ScoreBar bar = new ScoreBar(l.pts, l.max, col);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            rows.add(bar, g);

            JLabel pts = new JLabel(l.max == 0 ? "\u2014"
                : fmt(l.pts) + " / " + fmt(l.max));
            pts.setFont(Theme.f(Font.BOLD, 13));
            pts.setForeground(col);
            pts.setPreferredSize(new Dimension(70, 22));
            g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            rows.add(pts, g);

            JLabel note = new JLabel(l.note);
            note.setFont(Theme.f(Font.PLAIN, 12));
            note.setForeground(Theme.MUTED);
            note.setPreferredSize(new Dimension(190, 22));
            g.gridx = 3;
            rows.add(note, g);
        }
        box.add(rows, BorderLayout.CENTER);

        JLabel foot = new JLabel("<html><body style='width:640px'>"
            + "Index = Documents 15% + Skills 15% + Livelihood 25% + Mind 20% + "
            + "Family 15% + Legal 10%. It is a decision-support tool, not a "
            + "prediction of who will reoffend; the weights are a starting point "
            + "that experts would adjust.</body></html>");
        foot.setFont(Theme.f(Font.PLAIN, 11));
        foot.setForeground(Theme.MUTED);
        box.add(foot, BorderLayout.SOUTH);

        detail.removeAll();
        detail.add(box, BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }

    // ------------------------------------------------------------------
    // Scheme matcher
    // ------------------------------------------------------------------
    private void buildSchemes() {

        schemes.removeAll();
        CompassEngine.Match m = CompassEngine.match(allSchemes, current, 2);
        int weak = current.weakest();

        schemes.add(Theme.text("Your weakest pillar is " + CompassEngine.PILLARS[weak]
            + ". " + (m.schemes.isEmpty() ? "" : m.fallback
                ? "No active scheme is tagged for it yet, so these support "
                    + CompassEngine.PILLARS[m.pillar] + ":"
                : "These schemes strengthen it:"),
            300, 13, Theme.TEXT));
        schemes.add(Box.createVerticalStrut(8));

        if (m.schemes.isEmpty()) {
            schemes.add(Theme.text("No active scheme is tagged for any pillar yet. "
                + "An administrator can tag schemes in Government Schemes.",
                300, 12, Theme.MUTED));
        }
        for (CompassEngine.SchemeInfo s : m.schemes) {
            schemes.add(schemeCard(s, m.pillar));
            schemes.add(Box.createVerticalStrut(8));
        }

        Theme.Button all = new Theme.Button("Open Government Schemes",
            Theme.GREEN, Color.WHITE);
        all.addActionListener(e -> RoleBasedDashboard.open("Government Schemes"));
        schemes.add(all);
        schemes.revalidate();
        schemes.repaint();
    }

    private JPanel schemeCard(CompassEngine.SchemeInfo s, int pillar) {

        Color col = CompassVisuals.PILLAR_COLORS[pillar];
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.quality(g);
                g.setColor(CompassVisuals.lerp(Color.WHITE, col, 0.08));
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
                g.setColor(col);
                g.fill(new RoundRectangle2D.Double(0, 0, 5, getHeight() - 1, 5, 5));
                g.dispose();
                super.paintComponent(g0);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel n = Theme.text(s.name, 270, 14, Theme.TEXT);
        n.setFont(Theme.f(Font.BOLD, 14));
        p.add(n);
        String meta = (s.dept.isEmpty() ? "" : s.dept)
            + (s.type.isEmpty() ? "" : (s.dept.isEmpty() ? "" : "  \u00B7  ") + s.type);
        if (!meta.isEmpty()) {
            p.add(Theme.text(meta, 270, 11, Theme.MUTED));
        }
        if (!s.benefits.isEmpty()) {
            p.add(Box.createVerticalStrut(4));
            p.add(Theme.text(Theme.shorten(s.benefits, 110), 270, 12, Theme.TEXT));
        }
        if (s.auto) {
            p.add(Box.createVerticalStrut(4));
            p.add(Theme.text("Pillar guessed from keywords", 270, 10, Theme.MUTED));
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 20));
        return p;
    }

    // ------------------------------------------------------------------
    /** Animated "points / max" bar used in the pillar zoom. */
    static class ScoreBar extends CompassVisuals.AnimPanel {

        private final double pts;
        private final double max;
        private final Color col;

        ScoreBar(double pts, double max, Color col) {
            this.pts = pts;
            this.max = max;
            this.col = col;
            durationMs = 800;
            setPreferredSize(new Dimension(200, 16));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.quality(g);
            int w = getWidth();
            int h = 12;
            int y = (getHeight() - h) / 2;
            g.setColor(new Color(226, 234, 229));
            g.fill(new RoundRectangle2D.Double(0, y, w, h, h, h));
            if (max > 0 && pts > 0) {
                double fw = Math.max(h, w * (pts / max) * CompassVisuals.easeOut(t));
                g.setPaint(new GradientPaint(0, 0, CompassVisuals.lerp(col, Color.WHITE, 0.3),
                    (float) fw, 0, col));
                g.fill(new RoundRectangle2D.Double(0, y, fw, h, h, h));
            }
            g.dispose();
        }
    }
}