package rebirth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * REbirth - Growth Journey (prisoner).
 *
 * The growth tree from the dashboard, full size.
 *   - 5 numbered circles sit on the tree = the 5 milestones.
 *   - Click a circle: the view zooms into it and the side panel explains
 *     what to do next, with a button that opens the right screen.
 *   - Click empty space, press Esc or use "Whole tree" to zoom out.
 *   - Readiness score (0-100) = 50% milestones + 30% rehabilitation goals
 *     + 20% completed counselling sessions.
 *   - When all 5 milestones are done the prisoner can download a
 *     "Certificate of Rebirth" (PNG drawn with Java2D).
 *
 * Uses tree_0.png ... tree_4.png from the rebirth package. If an image is
 * missing, a simple tree is drawn instead.
 */
public class TreeJourneyForm extends JFrame {

    private static final String[] KEYS = {
        "Support Requests", "Opportunities", "My Sessions",
        "Opportunities", "My Profile"
    };
    private static final String[] BUTTONS = {
        "Raise a support request", "Browse opportunities",
        "View my sessions", "Find a course or training", "Update my profile"
    };
    private static final String[] HINTS = {
        "Ask for help: counselling, legal aid, healthcare or a job.",
        "Apply for a job or training that fits your skills.",
        "Attend a counselling session. Your counsellor marks it Completed.",
        "Finish a course or training. The NGO marks your application Completed.",
        "Secure a job. Your employment status in your profile shows Employed."
    };

    // where each numbered circle sits on the tree picture (0..1 of the image)
    private static final double[][] NODES = {
        {0.50, 0.86}, {0.30, 0.66}, {0.70, 0.60}, {0.36, 0.38}, {0.62, 0.22}
    };

    private final int userId;
    private final String fullName;

    private final boolean[] done = new boolean[5];
    private final int[] counts = new int[5];
    private int stage;
    private int goalsTotal;
    private int goalsDone;
    private int readiness;
    private boolean loaded = false;
    private boolean loading = false;
    private int selected = -1;

    private final BufferedImage[] images = new BufferedImage[5];

    private final TreeCanvas canvas = new TreeCanvas();
    private final JPanel detail = new JPanel();
    private final JLabel scoreLabel = new JLabel("...");
    private final JLabel stageLabel = new JLabel(" ");
    private final JLabel goalsLabel = new JLabel(" ");
    private final Theme.ProgressBar scoreBar = new Theme.ProgressBar();
    private final JPanel certificateBox = new JPanel();

    public TreeJourneyForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.fullName = fullName;

        setTitle("REbirth - Growth Journey");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        for (int i = 0; i < images.length; i++) {
            images[i] = RoleBasedDashboard.loadImage("tree_" + i + ".png");
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("My Growth Journey",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 20, 24));
        body.add(canvas, BorderLayout.CENTER);
        body.add(buildSidePanel(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        // Esc = zoom out
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("ESCAPE"), "zoomOut");
        getRootPane().getActionMap().put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selected = -1;
                showDetail();
                canvas.zoomTo(-1);
            }
        });

        // coming back from another screen: refresh the numbers
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (loaded) {
                    load();
                }
            }
        });

        showDetail();
        load();
    }

    // ------------------------------------------------------------------
    // Side panel
    // ------------------------------------------------------------------
    private JPanel buildSidePanel() {

        Theme.Card card = new Theme.Card(Color.WHITE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        card.setPreferredSize(new Dimension(360, 100));

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel head = new JLabel("Readiness Score");
        head.setFont(Theme.f(Font.BOLD, 14));
        head.setForeground(Theme.MUTED);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);

        scoreLabel.setFont(Theme.f(Font.BOLD, 44));
        scoreLabel.setForeground(Theme.GREEN_DARK);
        scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        stageLabel.setFont(Theme.f(Font.BOLD, 15));
        stageLabel.setForeground(Theme.TEXT);
        stageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        goalsLabel.setFont(Theme.f(Font.PLAIN, 13));
        goalsLabel.setForeground(Theme.MUTED);
        goalsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        scoreBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel formula = new JLabel(
            "<html>50% milestones + 30% rehabilitation goals + 20% counselling</html>");
        formula.setFont(Theme.f(Font.PLAIN, 11));
        formula.setForeground(Theme.MUTED);
        formula.setAlignmentX(Component.LEFT_ALIGNMENT);

        Theme.Button planBtn = new Theme.Button("Open my rehabilitation plan",
            Theme.GREEN_SOFT, Theme.GREEN_DARK);
        planBtn.addActionListener(e -> RoleBasedDashboard.open("Rehabilitation Plan"));

        col.add(head);
        col.add(Box.createVerticalStrut(2));
        col.add(scoreLabel);
        col.add(Box.createVerticalStrut(4));
        col.add(scoreBar);
        col.add(Box.createVerticalStrut(6));
        col.add(formula);
        col.add(Box.createVerticalStrut(14));
        col.add(stageLabel);
        col.add(Box.createVerticalStrut(2));
        col.add(goalsLabel);
        col.add(Box.createVerticalStrut(8));
        col.add(planBtn);
        col.add(Box.createVerticalStrut(14));
        col.add(new JSeparator());
        col.add(Box.createVerticalStrut(14));

        detail.setOpaque(false);
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(detail);

        certificateBox.setOpaque(false);
        certificateBox.setLayout(new BoxLayout(certificateBox, BoxLayout.Y_AXIS));
        certificateBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(Box.createVerticalStrut(12));
        col.add(certificateBox);

        card.add(col, BorderLayout.NORTH);
        return card;
    }

    private int nextStep() {
        for (int i = 0; i < done.length; i++) {
            if (!done[i]) {
                return i;
            }
        }
        return -1;
    }

    private void showDetail() {

        detail.removeAll();

        if (selected < 0) {
            int next = nextStep();
            JLabel t = new JLabel("Your next step");
            t.setFont(Theme.f(Font.BOLD, 14));
            t.setForeground(Theme.MUTED);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.add(t);
            detail.add(Box.createVerticalStrut(4));

            if (!loaded) {
                detail.add(Theme.text("Loading your journey...", 300, 14, Theme.MUTED));
            } else if (next < 0) {
                detail.add(Theme.text("All five milestones are complete. "
                    + "Your tree has grown into a Banyan Tree.", 300, 14, Theme.TEXT));
            } else {
                detail.add(Theme.text(RoleBasedDashboard.MILESTONES[next], 300, 16,
                    Theme.TEXT));
                detail.add(Box.createVerticalStrut(6));
                detail.add(Theme.text("Click circle " + (next + 1)
                    + " on the tree to zoom in.", 300, 13, Theme.MUTED));
            }
        } else {
            int i = selected;
            int next = nextStep();

            JLabel num = new JLabel("Milestone " + (i + 1) + " of 5");
            num.setFont(Theme.f(Font.BOLD, 13));
            num.setForeground(Theme.MUTED);
            num.setAlignmentX(Component.LEFT_ALIGNMENT);

            detail.add(num);
            detail.add(Box.createVerticalStrut(4));
            detail.add(Theme.text(RoleBasedDashboard.MILESTONES[i], 300, 18, Theme.TEXT));
            detail.add(Box.createVerticalStrut(8));

            Theme.Badge badge = done[i]
                ? new Theme.Badge("Completed", Theme.GREEN, Color.WHITE)
                : i == next
                    ? new Theme.Badge("Your next step", Theme.ORANGE, Color.WHITE)
                    : new Theme.Badge("Not yet", new Color(130, 140, 135), Color.WHITE);
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.add(badge);
            detail.add(Box.createVerticalStrut(10));

            detail.add(Theme.text(HINTS[i], 300, 13, Theme.MUTED));
            if (counts[i] > 0) {
                detail.add(Box.createVerticalStrut(6));
                detail.add(Theme.text("Recorded so far: " + counts[i], 300, 13,
                    Theme.GREEN_DARK));
            }
            detail.add(Box.createVerticalStrut(12));

            Theme.Button go = new Theme.Button(BUTTONS[i], Theme.GREEN, Color.WHITE);
            final String key = KEYS[i];
            go.addActionListener(e -> RoleBasedDashboard.open(key));
            detail.add(go);
            detail.add(Box.createVerticalStrut(8));

            Theme.Button whole = new Theme.Button("\u2190  Whole tree",
                Theme.GREEN_SOFT, Theme.GREEN_DARK);
            whole.addActionListener(e -> {
                selected = -1;
                showDetail();
                canvas.zoomTo(-1);
            });
            detail.add(whole);
        }

        detail.revalidate();
        detail.repaint();
    }

    private void showCertificateBox() {

        certificateBox.removeAll();

        if (stage >= 5) {
            Theme.Button cert = new Theme.Button("Download my Rebirth Certificate",
                Theme.GREEN_DARK, Color.WHITE);
            cert.addActionListener(e -> saveCertificate());
            certificateBox.add(cert);
        } else {
            certificateBox.add(Theme.text("Complete all five milestones to earn your "
                + "Certificate of Rebirth.", 300, 12, Theme.MUTED));
        }
        certificateBox.revalidate();
        certificateBox.repaint();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------
    /** Same formula is used by the dashboard. */
    static int readiness(int milestones, int goalsDone, int goalsTotal, int sessions) {
        double m = milestones / 5.0;
        double g = goalsTotal == 0 ? 0 : (double) goalsDone / goalsTotal;
        double s = Math.min(sessions, 5) / 5.0;
        return (int) Math.round(100 * (0.5 * m + 0.3 * g + 0.2 * s));
    }

    private static class Data {
        boolean[] done = new boolean[5];
        int[] counts = new int[5];
        int goalsTotal;
        int goalsDone;
        String error;
    }

    private void load() {

        if (loading) {
            return;
        }
        loading = true;
        final boolean first = !loaded;

        new SwingWorker<Data, Void>() {
            @Override
            protected Data doInBackground() {
                return fetch();
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    apply(get(), first);
                } catch (Exception ex) {
                    CounsellorUtil.error(TreeJourneyForm.this, String.valueOf(ex.getMessage()));
                }
            }
        }.execute();
    }

    private Data fetch() {

        Data d = new Data();

        try (Connection con = DBConnection.getConnection()) {

            CounsellorUtil.ensureTables(con);
            int pid = CounsellorUtil.prisonerId(con, userId);

            d.counts[0] = count(con,
                "SELECT COUNT(*) FROM SUPPORT_REQUESTS WHERE PRISONER_ID = ?", pid);
            d.counts[1] = count(con,
                "SELECT COUNT(*) FROM OPPORTUNITY_APPLICATIONS WHERE PRISONER_ID = ?", pid);
            d.counts[2] = count(con,
                "SELECT COUNT(*) FROM COUNSELLING_SESSIONS "
                + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'", pid);
            d.counts[3] = count(con,
                "SELECT COUNT(*) FROM OPPORTUNITY_APPLICATIONS "
                + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'", pid);
            d.counts[4] = count(con,
                "SELECT COUNT(*) FROM PRISONER_DETAILS WHERE PRISONER_ID = ? "
                + "AND UPPER(TRIM(EMPLOYMENT_STATUS)) = 'EMPLOYED'", pid);

            for (int i = 0; i < 5; i++) {
                d.done[i] = d.counts[i] > 0;
            }

            d.goalsTotal = count(con,
                "SELECT COUNT(*) FROM REHABILITATION_PLANS WHERE PRISONER_ID = ?", pid);
            d.goalsDone = count(con,
                "SELECT COUNT(*) FROM REHABILITATION_PLANS "
                + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'", pid);

        } catch (SQLException ex) {
            d.error = ex.getMessage();
        }
        return d;
    }

    private static int count(Connection con, String sql, int id) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            return 0;          // table not created yet
        }
    }

    private void apply(Data d, boolean first) {

        if (d.error != null) {
            CounsellorUtil.error(this, "Could not load your journey:\n" + d.error);
        }

        int milestones = 0;
        for (int i = 0; i < 5; i++) {
            done[i] = d.done[i];
            counts[i] = d.counts[i];
            if (done[i]) {
                milestones++;
            }
        }
        stage = milestones;
        goalsTotal = d.goalsTotal;
        goalsDone = d.goalsDone;
        readiness = readiness(milestones, goalsDone, goalsTotal, counts[2]);
        loaded = true;

        scoreLabel.setText(readiness + " / 100");
        scoreBar.setValue(readiness / 100.0);
        stageLabel.setText("Stage " + stage + " of 5:  " + RoleBasedDashboard.STAGE_NAMES[stage]);
        goalsLabel.setText(goalsTotal == 0 ? "No rehabilitation goals yet"
            : goalsDone + " of " + goalsTotal + " rehabilitation goals completed");

        showDetail();
        showCertificateBox();

        if (first) {
            canvas.zoomTo(-1);       // the opening "zoom in" onto the whole tree
        } else {
            canvas.repaint();
        }
    }

    // ------------------------------------------------------------------
    // Certificate
    // ------------------------------------------------------------------
    private void saveCertificate() {

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("REbirth_Certificate.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        int w = 1100;
        int h = 780;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Theme.quality(g2);

        g2.setPaint(new GradientPaint(0, 0, new Color(252, 250, 240),
            0, h, new Color(238, 246, 236)));
        g2.fillRect(0, 0, w, h);

        g2.setColor(Theme.GREEN_DARK);
        g2.setStroke(new BasicStroke(8f));
        g2.drawRect(24, 24, w - 48, h - 48);
        g2.setColor(Theme.GREEN);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(40, 40, w - 80, h - 80);

        centre(g2, "REbirth", new Font("Georgia", Font.BOLD, 30), Theme.GREEN, w, 120);
        centre(g2, "Certificate of Rebirth", new Font("Georgia", Font.BOLD, 58),
            Theme.GREEN_DARK, w, 205);
        centre(g2, "This is proudly presented to", new Font("Georgia", Font.ITALIC, 22),
            Theme.TEXT, w, 275);
        centre(g2, fullName, new Font("Georgia", Font.BOLD, 52), Theme.TEXT, w, 360);

        g2.setColor(Theme.GREEN);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(w / 2 - 260, 380, w / 2 + 260, 380);

        centre(g2, "who completed all five milestones of the REbirth journey",
            new Font("Georgia", Font.PLAIN, 22), Theme.TEXT, w, 430);
        centre(g2, "and grew from a seed into a Banyan Tree.",
            new Font("Georgia", Font.PLAIN, 22), Theme.TEXT, w, 464);
        centre(g2, "Readiness score: " + readiness + " / 100",
            new Font("Georgia", Font.BOLD, 24), Theme.GREEN_DARK, w, 520);

        BufferedImage tree = images[4];
        if (tree != null) {
            double s = Math.min(150.0 / tree.getWidth(), 150.0 / tree.getHeight());
            int tw = (int) (tree.getWidth() * s);
            int th = (int) (tree.getHeight() * s);
            g2.drawImage(tree, w / 2 - tw / 2, 545, tw, th, null);
        }

        centre(g2, new SimpleDateFormat("dd MMMM yyyy").format(new Date()),
            new Font("Georgia", Font.ITALIC, 18), Theme.MUTED, w, h - 62);

        g2.dispose();

        try {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getPath() + ".png");
            }
            ImageIO.write(img, "png", file);
            CounsellorUtil.info(this, "Certificate saved:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            CounsellorUtil.error(this, "Could not save the certificate: " + ex.getMessage());
        }
    }

    private static void centre(Graphics2D g2, String text, Font f, Color c, int w, int y) {
        g2.setFont(f);
        g2.setColor(c);
        g2.drawString(text, (w - g2.getFontMetrics().stringWidth(text)) / 2, y);
    }

    // ------------------------------------------------------------------
    // The zoomable tree
    // ------------------------------------------------------------------
    private class TreeCanvas extends JComponent {

        private double scale = 0.6;     // starts small, so opening feels like a zoom-in
        private double tx = 0;
        private double ty = 0;
        private Timer anim;
        private final Rectangle2D.Double pic = new Rectangle2D.Double();

        TreeCanvas() {
            setPreferredSize(new Dimension(600, 500));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int hit = hitNode(e.getPoint());
                    if (hit >= 0) {
                        selected = hit;
                        showDetail();
                        zoomTo(hit);
                    } else if (selected >= 0) {
                        selected = -1;
                        showDetail();
                        zoomTo(-1);
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(hitNode(e.getPoint()) >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                }
            });
        }

        private BufferedImage current() {
            return images[Math.max(0, Math.min(4, stage))];
        }

        private void layoutPicture() {
            int w = getWidth();
            int h = getHeight();
            BufferedImage im = current();
            double iw = im != null ? im.getWidth() : 600;
            double ih = im != null ? im.getHeight() : 700;
            double s = Math.min((w - 40) / iw, (h - 40) / ih);
            pic.setRect((w - iw * s) / 2, (h - ih * s) / 2, iw * s, ih * s);
        }

        private Point2D.Double node(int i) {
            return new Point2D.Double(pic.x + NODES[i][0] * pic.width,
                                      pic.y + NODES[i][1] * pic.height);
        }

        private int hitNode(Point p) {
            layoutPicture();
            double wx = (p.x - tx) / scale;
            double wy = (p.y - ty) / scale;
            for (int i = 0; i < NODES.length; i++) {
                if (node(i).distance(wx, wy) <= 22) {
                    return i;
                }
            }
            return -1;
        }

        /** index = milestone to zoom into, -1 = whole tree. One short animation. */
        void zoomTo(int index) {

            layoutPicture();
            final double s0 = scale;
            final double x0 = tx;
            final double y0 = ty;
            final double s1 = index < 0 ? 1.0 : 2.3;
            double x1 = 0;
            double y1 = 0;
            if (index >= 0) {
                Point2D.Double n = node(index);
                x1 = getWidth() / 2.0 - n.x * s1;
                y1 = getHeight() / 2.0 - n.y * s1;
            }
            final double fx = x1;
            final double fy = y1;

            if (anim != null) {
                anim.stop();
            }
            final int[] step = {0};
            anim = new Timer(15, e -> {
                double t = ++step[0] / 20.0;
                t = t * t * (3 - 2 * t);
                scale = s0 + (s1 - s0) * t;
                tx = x0 + (fx - x0) * t;
                ty = y0 + (fy - y0) * t;
                repaint();
                if (step[0] >= 20) {
                    anim.stop();
                }
            });
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();
            layoutPicture();

            // card background
            g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, 22, 22));
            g2.setPaint(new GradientPaint(0, 0, new Color(226, 243, 233),
                0, h, new Color(252, 253, 244)));
            g2.fillRect(0, 0, w, h);

            // zoomed content
            g2.translate(tx, ty);
            g2.scale(scale, scale);

            BufferedImage im = current();
            if (im != null) {
                g2.drawImage(im, (int) pic.x, (int) pic.y,
                    (int) pic.width, (int) pic.height, null);
            } else {
                drawFallbackTree(g2);
            }

            int next = nextStep();
            for (int i = 0; i < NODES.length; i++) {
                drawNode(g2, i, i == next);
            }

            g2.setTransform(new AffineTransform());
            g2.setClip(null);

            g2.setFont(Theme.f(Font.PLAIN, 12));
            g2.setColor(Theme.MUTED);
            g2.drawString("Click a numbered circle to zoom in.   Esc = zoom out.",
                18, h - 16);
            g2.dispose();
        }

        private void drawNode(Graphics2D g2, int i, boolean isNext) {

            Point2D.Double n = node(i);
            double r = 15;

            if (i == selected) {
                g2.setColor(new Color(34, 120, 84, 70));
                g2.fill(new Ellipse2D.Double(n.x - r - 7, n.y - r - 7,
                    2 * r + 14, 2 * r + 14));
            }
            if (isNext && !done[i]) {
                g2.setColor(new Color(205, 105, 20, 90));
                g2.fill(new Ellipse2D.Double(n.x - r - 5, n.y - r - 5,
                    2 * r + 10, 2 * r + 10));
            }

            g2.setColor(done[i] ? Theme.GREEN : Color.WHITE);
            g2.fill(new Ellipse2D.Double(n.x - r, n.y - r, 2 * r, 2 * r));
            g2.setColor(done[i] ? Theme.GREEN_DARK : isNext ? Theme.ORANGE : Theme.BORDER);
            g2.setStroke(new BasicStroke(2.4f));
            g2.draw(new Ellipse2D.Double(n.x - r, n.y - r, 2 * r, 2 * r));

            g2.setColor(done[i] ? Color.WHITE : Theme.TEXT);
            g2.setFont(Theme.f(Font.BOLD, 15));
            String t = done[i] ? "\u2713" : String.valueOf(i + 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, (float) (n.x - fm.stringWidth(t) / 2.0),
                (float) (n.y + fm.getAscent() / 2.0 - 2));

            // label, only when zoomed into this circle
            if (i == selected && scale > 1.5) {
                String label = RoleBasedDashboard.MILESTONES[i];
                g2.setFont(Theme.f(Font.BOLD, 11));
                fm = g2.getFontMetrics();
                int lw = fm.stringWidth(label) + 18;
                double lx = n.x - lw / 2.0;
                double ly = n.y - r - 34;
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fill(new RoundRectangle2D.Double(lx + 1, ly + 2, lw, 22, 12, 12));
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(lx, ly, lw, 22, 12, 12));
                g2.setColor(Theme.GREEN_DARK);
                g2.drawString(label, (float) (lx + 9), (float) (ly + 15));
            }
        }

        /** Simple tree drawn in code when tree_N.png is missing. */
        private void drawFallbackTree(Graphics2D g2) {

            double cx = pic.x + pic.width / 2;
            double base = pic.y + pic.height * 0.95;
            int st = Math.max(0, Math.min(5, stage));
            double trunkH = pic.height * (0.18 + 0.07 * st);
            double trunkW = pic.width * (0.03 + 0.012 * st);

            g2.setColor(new Color(110, 78, 50));
            g2.fill(new RoundRectangle2D.Double(cx - trunkW / 2, base - trunkH,
                trunkW, trunkH, 8, 8));

            double crown = pic.width * (0.10 + 0.07 * st);
            g2.setColor(new Color(60, 150, 90));
            g2.fill(new Ellipse2D.Double(cx - crown, base - trunkH - crown * 1.1,
                crown * 2, crown * 1.6));
            g2.setColor(new Color(90, 180, 110));
            g2.fill(new Ellipse2D.Double(cx - crown * 0.7, base - trunkH - crown * 1.4,
                crown * 1.4, crown * 1.2));
        }
    }
}
