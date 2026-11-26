package rebirth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * REbirth - modern role-based dashboard.
 *
 * Layout 
 *   Sidebar (left)  |  Header bar
 *                   |  Welcome banner
 *                   |  4 stat cards
 *                   |  Growth Tree card  +  Opportunities card
 *                   |  Counselling card  +  Support card
 *
 * Images (put them in the package folder src/rebirth/):
 *   logo.png, banner.jpg, tree_0.png ... tree_4.png
 * The growth tree uses only the 5 tree images (no drawn tree).
 * logo.png and banner.jpg are optional (drawn fallbacks are used if missing).
 */
public class RoleBasedDashboard extends JFrame {

    // ------------------------------------------------------------------
    // Colours and fonts
    // ------------------------------------------------------------------
    static final Color GREEN_BTN = new Color(14, 112, 70);
    static final Color PAGE_BG   = new Color(238, 244, 239);
    static final Color TEXT      = new Color(28, 42, 35);
    static final Color MUTED     = new Color(105, 120, 112);

    static Font f(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    // The 6 growth stages (0 to 5 milestones completed)
    static final String[] STAGE_NAMES = {
        "Seed", "Sprout", "Small Plant",
        "Young Tree", "Strong Tree", "Banyan Tree"
    };

    // The 5 milestones that make the tree grow
    static final String[] MILESTONES = {
        "Support Request Raised",
        "Applied for an Opportunity",
        "Counselling Session Attended",
        "Course / Training Completed",
        "Employment Secured"
    };

    // SQL part reused to find "active" opportunities
    static final String ACTIVE_OPP =
        "FROM OPPORTUNITIES WHERE UPPER(STATUS) = 'ACTIVE' "
        + "AND (APPLICATION_DEADLINE IS NULL "
        + "OR APPLICATION_DEADLINE >= TRUNC(SYSDATE))";

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------
    private final int userId;
    private String fullName;
    private final String role;
    private final boolean isPrisoner;
    private final boolean isAdmin;
    private final boolean isCounsellor;

    private BufferedImage logoImage;
    private BufferedImage bannerImage;
    private final BufferedImage[] treeImages = new BufferedImage[5];

    private final JLabel[] statValues = new JLabel[4];
    private final MilestoneRow[] milestoneRows =
        new MilestoneRow[MILESTONES.length];
    private TreePanel treePanel;
    private JLabel stageLabel;
    private JLabel sessionLabel;
    private JPanel oppBody;
    static {
        Theme.install();
    }

    private BellButton bell;
    private final CompassVisuals.TnMap tnMap = new CompassVisuals.TnMap();
    private final JLabel[] tnLegend = new JLabel[3];
    private JLabel tnNote;
    private JLabel nameLabel;
    private JLabel readinessLabel;
    private JPanel attnBody;
    private boolean refreshing = false;

    // The open dashboard. Other screens use these two to go "back".
    private static RoleBasedDashboard instance;

    /** Brings the dashboard back to the front (used by every Back button). */
    static void showFront() {
        if (instance != null) {
            instance.setExtendedState(instance.getExtendedState() & ~Frame.ICONIFIED);
            instance.toFront();
            instance.requestFocus();
        }
    }

    /** Lets other screens open a module through the dashboard. */
    static void open(String module) {
        if (instance != null) {
            instance.openModule(module);
        }
    }

    // ------------------------------------------------------------------
    // Constructor (same signature as before, so LoginForm needs no change)
    // ------------------------------------------------------------------
    public RoleBasedDashboard(int userId, String fullName, String role) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.isPrisoner = role.equalsIgnoreCase("Prisoner");
        this.isAdmin = role.equalsIgnoreCase("Administrator")
                || role.equalsIgnoreCase("Admin");
        this.isCounsellor = role.equalsIgnoreCase("Counsellor");
        instance = this;

        setTitle("REbirth - Dashboard");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1050, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadImages();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE_BG);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(buildHeader(), BorderLayout.NORTH);
        right.add(buildScroll(), BorderLayout.CENTER);

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);

        loadDataInBackground(false);

        // coming back from another screen: refresh numbers, tree and lists
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                loadDataInBackground(true);
            }
        });
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------
    private void loadImages() {
        logoImage = loadImage("logo.png");
        bannerImage = loadImage("banner.png");
        for (int i = 0; i < treeImages.length; i++) {
            treeImages[i] = loadImage("tree_" + i + ".png");
            System.out.println("tree_" + i + ".png -> "
                    + (treeImages[i] != null ? "loaded" : "NOT FOUND"));
        }
    }

    static BufferedImage loadImage(String name) {
        try (InputStream in =
                 RoleBasedDashboard.class.getResourceAsStream("/rebirth/" + name)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static void setQuality(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // draws an image so that it fills w x h (cropping the extra part)
    private static void drawCover(Graphics2D g2, Image img, int w, int h) {
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        double scale = Math.max((double) w / iw, (double) h / ih);
        int dw = (int) (iw * scale);
        int dh = (int) (ih * scale);
        g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    // draws an image fully visible inside w x h (no cropping)
    private static void drawFit(Graphics2D g2, Image img, int w, int h) {
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        double scale = Math.min((double) w / iw, (double) h / ih);
        int dw = (int) (iw * scale);
        int dh = (int) (ih * scale);
        g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    // ------------------------------------------------------------------
    // Role configuration
    // ------------------------------------------------------------------
    // Each entry = { label shown in sidebar, module key }
    private List<String[]> navItems() {

        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"Dashboard", "Dashboard"});

        if (isPrisoner) {
            items.add(new String[]{"Opportunities", "Opportunities"});
            items.add(new String[]{"Support Requests", "Support Requests"});
            items.add(new String[]{"Counselling Sessions", "My Sessions"});
            items.add(new String[]{"Government Schemes", "Government Schemes"});
            items.add(new String[]{"Rehabilitation Plan", "Rehabilitation Plan"});
            items.add(new String[]{"My Compass", "My Compass"});

        } else if (role.equalsIgnoreCase("Counsellor")) {
            items.add(new String[]{"Schedule Session", "Schedule Session"});
            items.add(new String[]{"Session Notes", "Session Notes"});
            items.add(new String[]{"Rehabilitation Plan", "Update Rehabilitation Plan"});
            items.add(new String[]{"Support Requests", "Support Requests"});
            items.add(new String[]{"Early Warning", "Early Warning"});

        } else if (role.equalsIgnoreCase("NGO")) {
            items.add(new String[]{"Provide Assistance", "Provide Assistance"});
            items.add(new String[]{"Opportunities", "Opportunities"});
            items.add(new String[]{"Government Schemes", "Government Schemes"});
            items.add(new String[]{"Support Requests", "Support Requests"});

        } else if (isAdmin) {
            items.add(new String[]{"Manage Users", "Manage Users"});
            items.add(new String[]{"Manage NGOs", "Manage NGOs"});
            items.add(new String[]{"Opportunities", "Opportunities"});
            items.add(new String[]{"Government Schemes", "Government Schemes"});
            items.add(new String[]{"Support Requests", "Support Requests"});
            items.add(new String[]{"Command Centre", "Command Centre"});

        }

        items.add(new String[]{"My Profile", "My Profile"});
        items.add(new String[]{"Logout", "Logout"});
        return items;
    }

    private String[] statTitles() {
        if (isPrisoner) {
            return new String[]{"Available Opportunities", "My Support Requests",
                    "Milestones Completed", "Your Growth Stage"};
        }
        if (isAdmin) {
            return new String[]{"Registered Users", "Prisoners",
                    "Pending Requests", "Active Opportunities"};
        }
        if (isCounsellor) {
            return new String[]{"Sessions Today", "Upcoming Sessions",
                    "Completed Sessions", "Prisoners Counselled"};
        }
        return new String[]{"Pending Requests", "In Progress",
                "Resolved", "Active Opportunities"};
    }

    private String bannerSubtitle() {
        if (isPrisoner) {
            return "Keep going. You are on the right path.";
        }
        if (role.equalsIgnoreCase("Counsellor")) {
            return "Every conversation helps someone rebuild.";
        }
        if (role.equalsIgnoreCase("NGO")) {
            return "Your support turns second chances into futures.";
        }
        return "Keep the REbirth system running smoothly.";
    }

    private String[] bannerQuote() {
        if (isPrisoner) {
            return new String[]{"\"Your efforts", "are helping you grow.\""};
        }
        return new String[]{"\"Small steps,", "big change.\""};
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------
    private JPanel buildSidebar() {

        SidebarPanel side = new SidebarPanel();
        side.setLayout(new BorderLayout());
        side.setPreferredSize(new Dimension(240, 0));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(26, 16, 10, 16));

        LogoComponent logo = new LogoComponent();
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel name = new JLabel("REBirth");
        name.setFont(f(Font.BOLD, 28));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tag = new JLabel(
            "<html><center>Second Chance.<br>A Better Tomorrow.</center></html>");
        tag.setFont(f(Font.ITALIC, 12));
        tag.setForeground(new Color(200, 235, 212));
        tag.setAlignmentX(Component.CENTER_ALIGNMENT);

        top.add(logo);
        top.add(Box.createVerticalStrut(6));
        top.add(name);
        top.add(Box.createVerticalStrut(4));
        top.add(tag);
        top.add(Box.createVerticalStrut(26));

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(0, 14, 18, 0));

        for (String[] item : navItems()) {

            if (item[1].equals("Logout")) {
                nav.add(Box.createVerticalGlue());
            }

            NavItem button = new NavItem(item[0], item[0].equals("Dashboard"));
            final String key = item[1];
            button.addActionListener(e -> {
                if (key.equals("Logout")) {
                    logout();
                } else if (!key.equals("Dashboard")) {
                    openModule(key);
                }
            });

            nav.add(button);
            nav.add(Box.createVerticalStrut(4));
        }

        side.add(top, BorderLayout.NORTH);
        side.add(nav, BorderLayout.CENTER);
        return side;
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
            this, "Are you sure you want to logout?",
            "Confirm Logout", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
            new LoginForm().setVisible(true);
        }
    }

    // ------------------------------------------------------------------
    // Header bar
    // ------------------------------------------------------------------
    private JPanel buildHeader() {

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 232, 227)),
            BorderFactory.createEmptyBorder(12, 28, 12, 28)));

        JLabel title = new JLabel("Dashboard");
        title.setFont(f(Font.BOLD, 22));
        title.setForeground(TEXT);

        bell = new BellButton();
        bell.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int n = bell.badge;
                JOptionPane.showMessageDialog(RoleBasedDashboard.this,
                    n > 0 ? "You have " + n + " pending support request(s)."
                          : "No new notifications.");
            }
        });

        String initial = fullName.trim().isEmpty()
            ? "?" : fullName.trim().substring(0, 1).toUpperCase();

        nameLabel = new JLabel(fullName);
        nameLabel.setFont(f(Font.BOLD, 13));
        nameLabel.setForeground(TEXT);

        JLabel roleLabel = new JLabel(role);
        roleLabel.setFont(f(Font.PLAIN, 11));
        roleLabel.setForeground(MUTED);

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setOpaque(false);
        info.add(nameLabel);
        info.add(roleLabel);

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        rightBox.setOpaque(false);
        rightBox.add(bell);
        rightBox.add(new Avatar(initial));
        rightBox.add(info);

        header.add(title, BorderLayout.WEST);
        header.add(rightBox, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------------
    // Scrollable content area
    // ------------------------------------------------------------------
    private JScrollPane buildScroll() {

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(PAGE_BG);
        content.setBorder(BorderFactory.createEmptyBorder(20, 26, 10, 26));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.gridx = 0;
        g.weightx = 1;

        // Row 0: banner
        g.gridy = 0;
        g.gridwidth = 2;
        g.insets = new Insets(0, 0, 18, 0);
        content.add(new BannerPanel(), g);

        // Row 1: stat cards
        g.gridy = 1;
        content.add(buildStats(), g);

        // Row 2: tree (or quick actions) + opportunities
        g.gridy = 2;
        g.gridwidth = 1;
        g.gridx = 0;
        g.weightx = 0.42;
        g.insets = new Insets(0, 0, 18, 9);
        content.add(isPrisoner ? buildTreeCard() : buildQuickActionsCard(), g);

        g.gridx = 1;
        g.weightx = 0.58;
        g.insets = new Insets(0, 9, 18, 0);
        content.add(isCounsellor ? buildAttentionCard() : buildOpportunitiesCard(), g);

        // Row 3: counselling (or schemes) + support
        g.gridy = 3;
        g.gridx = 0;
        g.weightx = 0.42;
        g.insets = new Insets(0, 0, 18, 9);
        content.add(isPrisoner ? buildCounsellingCard() : buildSchemesCard(), g);

        g.gridx = 1;
        g.weightx = 0.58;
        g.insets = new Insets(0, 9, 18, 0);
        content.add(buildSupportCard(), g);

        // Row 4: Tamil Nadu overview (map + centres + NGOs)
        g.gridy = 4;
        g.gridx = 0;
        g.gridwidth = 2;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 18, 0);
        content.add(buildTnCard(), g);

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getViewport().setBackground(PAGE_BG);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    // ------------------------------------------------------------------
    // Stat cards
    // ------------------------------------------------------------------
    private JPanel buildStats() {

        Color[] fills = {
            new Color(226, 243, 226), new Color(222, 236, 252),
            new Color(234, 228, 250), new Color(253, 237, 220)
        };
        Color[] iconColors = {
            new Color(60, 160, 90), new Color(40, 110, 220),
            new Color(125, 90, 210), new Color(240, 150, 30)
        };
        String[] titles = statTitles();

        JPanel row = new JPanel(new GridLayout(1, 4, 18, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 125));

        for (int i = 0; i < 4; i++) {

            RoundedCard card = new RoundedCard(fills[i]);
            card.setLayout(new BorderLayout());
            card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

            JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            iconWrap.setOpaque(false);
            iconWrap.add(new IconCircle(iconColors[i], i));

            statValues[i] = new JLabel("...");
            statValues[i].setFont(f(Font.BOLD, 34));
            statValues[i].setForeground(TEXT);

            JLabel title = new JLabel("<html>" + titles[i] + "</html>");
            title.setFont(f(Font.PLAIN, 14));
            title.setForeground(new Color(55, 70, 62));

            JPanel bottom = new JPanel(new BorderLayout(12, 0));
            bottom.setOpaque(false);
            bottom.add(statValues[i], BorderLayout.WEST);
            bottom.add(title, BorderLayout.CENTER);

            card.add(iconWrap, BorderLayout.NORTH);
            card.add(bottom, BorderLayout.SOUTH);
            row.add(card);
        }
        return row;
    }

    // ------------------------------------------------------------------
    // Growth tree card (prisoner)
    // ------------------------------------------------------------------
    private JPanel buildTreeCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 300));

        card.add(cardHeader("Your Growth Tree", "Zoom into your tree",
                this::openJourney), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 10, 0));
        body.setOpaque(false);

        treePanel = new TreePanel();
        treePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        treePanel.setToolTipText("Click to zoom into your growth journey");
        treePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openJourney();
            }
        });
        body.add(treePanel);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.add(Box.createVerticalGlue());

        for (int i = 0; i < MILESTONES.length; i++) {
            milestoneRows[i] = new MilestoneRow(MILESTONES[i]);
            list.add(milestoneRows[i]);
            list.add(Box.createVerticalStrut(4));
        }

        stageLabel = new JLabel("Stage: ...");
        stageLabel.setFont(f(Font.BOLD, 14));
        stageLabel.setForeground(GREEN_BTN);
        stageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        readinessLabel = new JLabel("Readiness: ...");
        readinessLabel.setFont(f(Font.PLAIN, 13));
        readinessLabel.setForeground(MUTED);
        readinessLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        list.add(Box.createVerticalStrut(8));
        list.add(stageLabel);
        list.add(Box.createVerticalStrut(2));
        list.add(readinessLabel);
        list.add(Box.createVerticalGlue());

        body.add(list);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------
    // Quick actions card (counsellor / NGO / admin)
    // ------------------------------------------------------------------
    private JPanel buildQuickActionsCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 300));

        card.add(cardHeader("Quick Actions", null, null), BorderLayout.NORTH);

        Color[] pastel = {
            new Color(237, 230, 255), new Color(224, 242, 241),
            new Color(255, 239, 222), new Color(231, 238, 255),
            new Color(251, 230, 241), new Color(230, 244, 231)
        };

        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);

        int n = 0;
        for (String[] item : navItems()) {
            if (item[1].equals("Dashboard") || item[1].equals("Logout")) {
                continue;
            }
            RoundButton b = new RoundButton(item[0], pastel[n % pastel.length], TEXT);
            final String key = item[1];
            b.addActionListener(e -> openModule(key));
            grid.add(b);
            n++;
        }

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------
    // Opportunities card
    // ------------------------------------------------------------------
    private JPanel buildOpportunitiesCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 300));

        card.add(cardHeader("Latest Opportunities", "View All",
                () -> openModule("Opportunities")), BorderLayout.NORTH);

        oppBody = new JPanel(new GridLayout(1, 3, 12, 0));
        oppBody.setOpaque(false);

        JLabel loading = new JLabel("Loading opportunities...");
        loading.setFont(f(Font.PLAIN, 14));
        loading.setForeground(MUTED);
        oppBody.add(loading);

        card.add(oppBody, BorderLayout.CENTER);
        return card;
    }

    private void showOpportunities(List<String[]> opps) {

        oppBody.removeAll();

        if (opps.isEmpty()) {
            JLabel none = new JLabel("No active opportunities right now.");
            none.setFont(f(Font.PLAIN, 14));
            none.setForeground(MUTED);
            oppBody.add(none);
        } else {
            for (int i = 0; i < 3; i++) {
                if (i < opps.size()) {
                    oppBody.add(new OppCard(opps.get(i), i));
                } else {
                    JPanel filler = new JPanel();
                    filler.setOpaque(false);
                    oppBody.add(filler);
                }
            }
        }

        oppBody.revalidate();
        oppBody.repaint();
    }

    // ------------------------------------------------------------------
    // Needs Attention card (counsellor)
    // ------------------------------------------------------------------
    private JPanel buildAttentionCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 300));

        card.add(cardHeader("Needs Attention", "Schedule Session",
                () -> openModule("Schedule Session")), BorderLayout.NORTH);

        attnBody = new JPanel(new GridLayout(0, 1, 0, 8));
        attnBody.setOpaque(false);

        JLabel loading = new JLabel("Checking prisoners...");
        loading.setFont(f(Font.PLAIN, 14));
        loading.setForeground(MUTED);
        attnBody.add(loading);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(attnBody, BorderLayout.NORTH);

        card.add(wrap, BorderLayout.CENTER);
        return card;
    }

    private void showAttention(List<String[]> rows) {

        if (attnBody == null) {
            return;
        }
        attnBody.removeAll();

        if (rows.isEmpty()) {
            JLabel ok = new JLabel("All prisoners are on track. Nobody needs attention.");
            ok.setFont(f(Font.BOLD, 14));
            ok.setForeground(GREEN_BTN);
            attnBody.add(ok);
        } else {
            for (String[] r : rows) {
                attnBody.add(new AttentionRow(r[0], r[1]));
            }
        }
        attnBody.revalidate();
        attnBody.repaint();
    }

    // ------------------------------------------------------------------
    // Counselling card (prisoner)
    // ------------------------------------------------------------------
    private JPanel buildCounsellingCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 175));

        card.add(cardHeader("My Counselling Sessions", "View All",
                () -> openModule("My Sessions")), BorderLayout.NORTH);

        JLabel next = new JLabel("Next Session");
        next.setFont(f(Font.BOLD, 14));
        next.setForeground(MUTED);

        sessionLabel = new JLabel("Loading...");
        sessionLabel.setFont(f(Font.BOLD, 19));
        sessionLabel.setForeground(TEXT);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
        text.setOpaque(false);
        text.add(next);
        text.add(sessionLabel);

        RoundButton book = new RoundButton("Book Counselling", GREEN_BTN, Color.WHITE);
        book.addActionListener(e -> openModule("Support Requests"));

        JPanel btnWrap = new JPanel(new GridBagLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(book);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(text, BorderLayout.CENTER);
        body.add(btnWrap, BorderLayout.EAST);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------
    // Government schemes card (other roles)
    // ------------------------------------------------------------------
    private JPanel buildSchemesCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 175));

        card.add(cardHeader("Government Schemes", "View All",
                () -> openModule("Government Schemes")), BorderLayout.NORTH);

        JLabel text = new JLabel(
            "<html>Browse and manage the government schemes available "
            + "for rehabilitation and reintegration.</html>");
        text.setFont(f(Font.PLAIN, 14));
        text.setForeground(TEXT);

        RoundButton open = new RoundButton("Open Schemes", GREEN_BTN, Color.WHITE);
        open.addActionListener(e -> openModule("Government Schemes"));

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(open);

        card.add(text, BorderLayout.CENTER);
        card.add(btnWrap, BorderLayout.SOUTH);
        return card;
    }

    // ------------------------------------------------------------------
    // Support & assistance card
    // ------------------------------------------------------------------
    private JPanel buildSupportCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 175));

        card.add(cardHeader("Support & Assistance", null, null),
                BorderLayout.NORTH);

        JLabel need = new JLabel(isPrisoner ? "Need Help?" : "Support Requests");
        need.setFont(f(Font.BOLD, 16));
        need.setForeground(TEXT);

        JLabel desc = new JLabel(isPrisoner
            ? "<html>Raise a support request or connect with an NGO "
              + "for assistance.</html>"
            : "<html>Review requests from prisoners and follow up on them."
              + "</html>");
        desc.setFont(f(Font.PLAIN, 13));
        desc.setForeground(new Color(40, 90, 150));

        RoundButton b = new RoundButton(
            isPrisoner ? "Request Support" : "View Support Requests",
            GREEN_BTN, Color.WHITE);
        b.addActionListener(e -> openModule("Support Requests"));

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(b);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(need, BorderLayout.NORTH);
        left.add(desc, BorderLayout.CENTER);
        left.add(btnWrap, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setOpaque(false);
        body.add(left, BorderLayout.CENTER);
        body.add(new HeartIcon(), BorderLayout.EAST);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // Title row used at the top of every white card
    private JPanel buildTnCard() {

        RoundedCard card = new RoundedCard(Color.WHITE);
        card.setLayout(new BorderLayout(18, 8));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setPreferredSize(new Dimension(100, 560));

        card.add(cardHeader("Tamil Nadu Overview", null, null), BorderLayout.NORTH);
        card.add(tnMap, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(310, 100));
        side.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JLabel head = new JLabel("Rehabilitation network");
        head.setFont(f(Font.BOLD, 16));
        head.setForeground(TEXT);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(head);
        side.add(Box.createVerticalStrut(14));

        for (int i = 0; i < 3; i++) {
            final Color c = CompassVisuals.TnMap.TYPE_COLORS[i];
            JPanel row = new JPanel(new BorderLayout(12, 0)) {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g2 = (Graphics2D) g0.create();
                    setQuality(g2);
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 22));
                    g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1,
                        getHeight() - 1, 18, 18));
                    g2.setColor(c);
                    g2.fill(new RoundRectangle2D.Double(0, 0, 6, getHeight() - 1, 6, 6));
                    g2.dispose();
                    super.paintComponent(g0);
                }
            };
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 14));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

            tnLegend[i] = new JLabel("-");
            tnLegend[i].setFont(f(Font.BOLD, 28));
            tnLegend[i].setForeground(c.darker());
            JLabel lab = new JLabel(CompassEngine.CENTRE_TYPES[i] + "s");
            lab.setFont(f(Font.BOLD, 14));
            lab.setForeground(TEXT);
            row.add(tnLegend[i], BorderLayout.WEST);
            row.add(lab, BorderLayout.CENTER);
            side.add(row);
            side.add(Box.createVerticalStrut(10));
        }

        tnNote = new JLabel(" ");
        tnNote.setFont(f(Font.PLAIN, 13));
        tnNote.setForeground(MUTED);
        tnNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(Box.createVerticalStrut(6));
        side.add(tnNote);

        if (role.equalsIgnoreCase("Administrator") || role.equalsIgnoreCase("Admin")) {
            side.add(Box.createVerticalStrut(14));
            RoundButton manage = new RoundButton("Manage centres",
                GREEN_BTN, Color.WHITE);
            manage.addActionListener(e -> new CentresForm(fullName, role,
                () -> loadDataInBackground(true)).setVisible(true));
            side.add(manage);
        }
        card.add(side, BorderLayout.EAST);
        return card;
    }

    private void showTn(CompassEngine.CentreStats cs) {
        tnMap.setFacilities(cs.byDistrict);
        for (int i = 0; i < 3; i++) {
            tnLegend[i].setText(String.valueOf(cs.totals[i]));
        }
        int total = cs.totals[0] + cs.totals[1] + cs.totals[2];
        tnNote.setText(total == 0
            ? "<html>No centres recorded yet. An administrator can add them.</html>"
            : "<html>Present in " + cs.districtsCovered + " of "
                + (CompassEngine.DISTRICTS.length - 1)
                + " listed districts. Hover a district for its counts.</html>");
    }

    private JPanel cardHeader(String title, String link, Runnable action) {

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(228, 234, 230)),
            BorderFactory.createEmptyBorder(0, 0, 8, 0)));

        JLabel t = new JLabel(title);
        t.setFont(f(Font.BOLD, 17));
        t.setForeground(TEXT);
        p.add(t, BorderLayout.WEST);

        if (link != null) {
            JLabel l = new JLabel(link + "  \u2192");
            l.setFont(f(Font.BOLD, 12));
            l.setForeground(GREEN_BTN);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
            p.add(l, BorderLayout.EAST);
        }
        return p;
    }

    // ------------------------------------------------------------------
    // Opening other forms
    // ------------------------------------------------------------------
    private void openModule(String module) {
        try {
            openModuleImpl(module);
        } catch (Throwable ex) {
            // never fail silently: show what went wrong
            ex.printStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("Could not open \"").append(module).append("\".\n\n").append(ex);
            StackTraceElement[] st = ex.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) {
                sb.append("\n   at ").append(st[i]);
            }
            JOptionPane.showMessageDialog(this, sb.toString(), "REbirth",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openModuleImpl(String module) {

        switch (module) {

            case "Support Requests":
                new SupportRequestForm(userId, fullName, role).setVisible(true);
                break;

            case "Opportunities":
                new OpportunitiesForm(userId, fullName, role).setVisible(true);
                break;

            case "Government Schemes":
                new GovernmentSchemesForm(userId, fullName, role).setVisible(true);
                break;

            case "Rehabilitation Plan":            // prisoner (read-only)
            case "Update Rehabilitation Plan":     // counsellor (edit)
                new RehabilitationPlanForm(userId, fullName, role).setVisible(true);
                break;
            case "My Sessions":                    // prisoner (read-only)
            case "Schedule Session":               // counsellor (edit)
                new ScheduleSessionForm(userId, fullName, role).setVisible(true);
                break;
            case "Session Notes":
                new SessionNotesForm(userId, fullName, role).setVisible(true);
                break;
            case "My Compass":
                new CompassForm(userId, fullName, role).setVisible(true);
                break;
            case "Early Warning":
                new EarlyWarningForm(userId, fullName, role).setVisible(true);
                break;
            case "Command Centre":
                new CommandCentreForm(userId, fullName, role).setVisible(true);
                break;
            case "My Profile":
                new ProfileForm(userId, fullName, role, newName -> {
                    fullName = newName;
                    nameLabel.setText(newName);
                    repaint();
                }).setVisible(true);
                break;
            case "Provide Assistance":
                showModule("Provide Assistance",
                        "Record assistance provided to prisoners.");
                break;
            case "Manage Users":
                showModule("Manage Users", "View and manage registered users.");
                break;
            case "Manage NGOs":
                showModule("Manage NGOs", "View and manage NGO information.");
                break;
            case "Generate Reports":
                showModule("Generate Reports", "Generate project reports.");
                break;
            default:
                break;
        }
    }

    private void openJourney() {
        new TreeJourneyForm(userId, fullName, role).setVisible(true);
    }

    // Placeholder window for modules that are not built yet
    private void showModule(String title, String description) {

        JFrame frame = new JFrame(title);
        frame.setSize(520, 330);
        frame.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(PAGE_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel heading = new JLabel(title);
        heading.setFont(f(Font.BOLD, 24));
        heading.setForeground(GREEN_BTN);

        JLabel details = new JLabel("<html>" + description
            + "<br><br>This module is coming soon."
            + "<br><br>Logged-in user: " + fullName
            + "<br>User ID: " + userId
            + "<br>Role: " + role + "</html>");
        details.setFont(f(Font.PLAIN, 15));
        details.setForeground(TEXT);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(details, BorderLayout.CENTER);

        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    // ==================================================================
    // DATABASE LOADING (runs in the background so the window never freezes)
    // ==================================================================
    private static class DashData {
        String[] statText = {"-", "-", "-", "-"};
        int stage = 0;
        boolean[] done = new boolean[MILESTONES.length];
        List<String[]> opps = new ArrayList<>();
        String nextSession = null;
        int badge = 0;
        String error = null;
        int readiness = 0;
        List<String[]> attention = new ArrayList<>();
        CompassEngine.CentreStats centres;
    }

    private void loadDataInBackground(final boolean quiet) {

        if (refreshing) {
            return;
        }
        refreshing = true;

        new SwingWorker<DashData, Void>() {
            @Override
            protected DashData doInBackground() {
                return fetchData();
            }

            @Override
            protected void done() {
                refreshing = false;
                try {
                    applyData(get(), quiet);
                } catch (Exception ex) {
                    DashData empty = new DashData();
                    empty.error = ex.getMessage();
                    applyData(empty, quiet);
                }
            }
        }.execute();
    }

    private DashData fetchData() {

        DashData d = new DashData();

        try (Connection con = DBConnection.getConnection()) {

            CounsellorUtil.ensureTables(con);

            try {
                d.centres = CompassEngine.centreStats(con);
            } catch (SQLException ignored) {
                // map stays empty
            }

            int activeOpps = count(con, "SELECT COUNT(*) " + ACTIVE_OPP);
            d.opps = loadOpportunities(con);

            if (isPrisoner) {

                int pid = count(con,
                    "SELECT PRISONER_ID FROM PRISONER_DETAILS WHERE USER_ID = ?",
                    userId);

                int myRequests = count(con,
                    "SELECT COUNT(*) FROM SUPPORT_REQUESTS WHERE PRISONER_ID = ?",
                    pid);

                // Milestones. Tables 2 and 3 are optional (see notes):
                // if they do not exist yet, those milestones stay unchecked.
                d.done[0] = myRequests > 0;

                d.done[1] = count(con,
                    "SELECT COUNT(*) FROM OPPORTUNITY_APPLICATIONS "
                    + "WHERE PRISONER_ID = ?", pid) > 0;

                d.done[2] = count(con,
                    "SELECT COUNT(*) FROM COUNSELLING_SESSIONS "
                    + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'",
                    pid) > 0;

                d.done[3] = count(con,
                    "SELECT COUNT(*) FROM OPPORTUNITY_APPLICATIONS "
                    + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'",
                    pid) > 0;

                d.done[4] = count(con,
                    "SELECT COUNT(*) FROM PRISONER_DETAILS WHERE PRISONER_ID = ? "
                    + "AND UPPER(TRIM(EMPLOYMENT_STATUS)) = 'EMPLOYED'",
                    pid) > 0;

                int completed = 0;
                for (boolean b : d.done) {
                    if (b) {
                        completed++;
                    }
                }
                d.stage = completed;

                d.statText[0] = String.valueOf(activeOpps);
                d.statText[1] = String.valueOf(myRequests);
                d.statText[2] = completed + " / " + MILESTONES.length;
                d.statText[3] = STAGE_NAMES[d.stage];

                d.nextSession = loadNextSession(con, pid);

                int goalsTotal = count(con,
                    "SELECT COUNT(*) FROM REHABILITATION_PLANS WHERE PRISONER_ID = ?",
                    pid);
                int goalsDone = count(con,
                    "SELECT COUNT(*) FROM REHABILITATION_PLANS "
                    + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'", pid);
                int sessionsDone = count(con,
                    "SELECT COUNT(*) FROM COUNSELLING_SESSIONS "
                    + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'", pid);
                d.readiness = TreeJourneyForm.readiness(
                    completed, goalsDone, goalsTotal, sessionsDone);
                // use the Compass index so every screen shows the same number
                try {
                    d.readiness = (int) Math.round(
                        CompassEngine.compute(con, pid).index);
                } catch (SQLException ignored) {
                    // keep the older value
                }

            } else if (isAdmin) {

                int pending = countStatus(con, "Pending");
                d.statText[0] = String.valueOf(
                    count(con, "SELECT COUNT(*) FROM USERS"));
                d.statText[1] = String.valueOf(
                    count(con, "SELECT COUNT(*) FROM PRISONER_DETAILS"));
                d.statText[2] = String.valueOf(pending);
                d.statText[3] = String.valueOf(activeOpps);
                d.badge = pending;

            } else if (isCounsellor) {

                String me = "(SELECT COUNSELLOR_ID FROM COUNSELLOR_DETAILS "
                          + "WHERE USER_ID = ?)";
                String base = "SELECT COUNT(*) FROM COUNSELLING_SESSIONS "
                          + "WHERE COUNSELLOR_ID = " + me;

                d.statText[0] = String.valueOf(count(con, base
                    + " AND UPPER(STATUS) = 'SCHEDULED' "
                    + "AND TRUNC(SESSION_DATE) = TRUNC(SYSDATE)", userId));
                d.statText[1] = String.valueOf(count(con, base
                    + " AND UPPER(STATUS) = 'SCHEDULED' "
                    + "AND SESSION_DATE >= SYSDATE", userId));
                d.statText[2] = String.valueOf(count(con, base
                    + " AND UPPER(STATUS) = 'COMPLETED'", userId));
                d.statText[3] = String.valueOf(count(con,
                    "SELECT COUNT(DISTINCT PRISONER_ID) FROM COUNSELLING_SESSIONS "
                    + "WHERE COUNSELLOR_ID = " + me, userId));
                d.badge = countStatus(con, "Pending");
                d.attention = loadAttention(con);

            } else {

                int pending = countStatus(con, "Pending");
                d.statText[0] = String.valueOf(pending);
                d.statText[1] = String.valueOf(countStatus(con, "In Progress"));
                d.statText[2] = String.valueOf(countStatus(con, "Resolved"));
                d.statText[3] = String.valueOf(activeOpps);
                d.badge = pending;
            }

        } catch (SQLException e) {
            d.error = e.getMessage();
        }

        return d;
    }

    // Runs a COUNT-style query. Returns 0 if the query fails or returns nothing.
    private static int count(Connection con, String sql, Object... params) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private static int countStatus(Connection con, String status) {
        return count(con,
            "SELECT COUNT(*) FROM SUPPORT_REQUESTS WHERE STATUS = ?", status);
    }

    private List<String[]> loadOpportunities(Connection con) {

        List<String[]> list = new ArrayList<>();

        String sql =
            "SELECT * FROM (SELECT OPPORTUNITY_TITLE, ORGANIZATION_NAME, "
            + "OPPORTUNITY_TYPE " + ACTIVE_OPP
            + " ORDER BY OPPORTUNITY_ID DESC) WHERE ROWNUM <= 3";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new String[]{
                    nz(rs.getString(1)), nz(rs.getString(2)), nz(rs.getString(3))
                });
            }
        } catch (SQLException e) {
            // leave the list empty
        }
        return list;
    }

    private String loadNextSession(Connection con, int prisonerId) {

        String sql =
            "SELECT TO_CHAR(SESSION_DATE, 'DD-Mon-YYYY  HH12:MI AM') FROM ("
            + "SELECT SESSION_DATE FROM COUNSELLING_SESSIONS "
            + "WHERE PRISONER_ID = ? AND SESSION_DATE >= SYSDATE "
            + "AND UPPER(STATUS) = 'SCHEDULED' ORDER BY SESSION_DATE) "
            + "WHERE ROWNUM = 1";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prisonerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // table may not exist yet
        }
        return null;
    }

    /**
     * Prisoners a counsellor should look at: no completed session in the last
     * 30 days (or never), or an average progress rating of 2 or less.
     */
    private List<String[]> loadAttention(Connection con) {

        List<String[]> list = new ArrayList<>();

        String sql =
            "SELECT * FROM (SELECT u.FULL_NAME, MAX(s.SESSION_DATE), "
            + "AVG(s.PROGRESS_RATING) FROM PRISONER_DETAILS p "
            + "JOIN USERS u ON u.USER_ID = p.USER_ID "
            + "LEFT JOIN COUNSELLING_SESSIONS s ON s.PRISONER_ID = p.PRISONER_ID "
            + "AND UPPER(s.STATUS) = 'COMPLETED' "
            + "GROUP BY p.PRISONER_ID, u.FULL_NAME "
            + "HAVING MAX(s.SESSION_DATE) IS NULL "
            + "OR MAX(s.SESSION_DATE) < SYSDATE - 30 "
            + "OR AVG(s.PROGRESS_RATING) <= 2 "
            + "ORDER BY MAX(s.SESSION_DATE) NULLS FIRST) WHERE ROWNUM <= 4";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Timestamp last = rs.getTimestamp(2);
                double avg = rs.getDouble(3);
                boolean hasAvg = !rs.wasNull();

                List<String> why = new ArrayList<>();
                if (last == null) {
                    why.add("No session yet");
                } else {
                    long days = (System.currentTimeMillis() - last.getTime())
                            / 86400000L;
                    if (days > 30) {
                        why.add("Last session " + days + " days ago");
                    }
                }
                if (hasAvg && avg <= 2) {
                    why.add("Low progress rating (" + String.format("%.1f", avg) + ")");
                }
                list.add(new String[]{nz(rs.getString(1)), String.join("  |  ", why)});
            }
        } catch (SQLException e) {
            // leave the list empty
        }
        return list;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // Put the loaded data on screen (runs on the Swing thread)
    private void applyData(DashData d, boolean quiet) {

        for (int i = 0; i < 4; i++) {
            String text = d.statText[i];
            statValues[i].setText(text);
            statValues[i].setFont(f(Font.BOLD, text.length() > 5 ? 22 : 34));
        }

        if (isPrisoner && treePanel != null) {
            treePanel.setStage(d.stage);
            for (int i = 0; i < milestoneRows.length; i++) {
                milestoneRows[i].setDone(d.done[i]);
            }
            stageLabel.setText("Stage " + d.stage + "/5: " + STAGE_NAMES[d.stage]);
            readinessLabel.setText("Readiness score: " + d.readiness + " / 100");
            sessionLabel.setText(d.nextSession == null
                ? "No upcoming session" : d.nextSession);
        }

        if (isCounsellor) {
            showAttention(d.attention);
        } else {
            showOpportunities(d.opps);
        }
        bell.setBadge(d.badge);
        if (d.centres != null) {
            showTn(d.centres);
        }

        if (d.error != null && !quiet) {
            JOptionPane.showMessageDialog(this,
                "Could not load live data from the database:\n" + d.error,
                "Database", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ==================================================================
    // CUSTOM COMPONENTS
    // ==================================================================

    // ---- Sidebar background ------------------------------------------
    private static class SidebarPanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int w = getWidth();
            int h = getHeight();

            g2.setPaint(new GradientPaint(0, 0, new Color(6, 52, 32),
                    0, h, new Color(20, 104, 62)));
            g2.fillRect(0, 0, w, h);

            // decorative leaves at the bottom
            g2.setColor(new Color(70, 180, 100, 55));
            AffineTransform old = g2.getTransform();
            g2.rotate(Math.toRadians(-35), w * 0.3, h - 60);
            g2.fill(new Ellipse2D.Double(w * 0.3 - 20, h - 190, 60, 150));
            g2.setTransform(old);
            g2.rotate(Math.toRadians(30), w * 0.65, h - 50);
            g2.fill(new Ellipse2D.Double(w * 0.65 - 20, h - 170, 55, 130));
            g2.setTransform(old);

            g2.dispose();
        }
    }

    // ---- Logo (image if available, otherwise a drawn tree) -----------
    private class LogoComponent extends JComponent {

        LogoComponent() {
            setPreferredSize(new Dimension(96, 96));
            setMaximumSize(new Dimension(96, 96));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);
            int w = getWidth();
            int h = getHeight();

            if (logoImage != null) {
                drawFit(g2, logoImage, w, h);
            } else {
                g2.setColor(new Color(110, 200, 90));
                g2.fill(new Ellipse2D.Double(w * 0.12, h * 0.05, w * 0.5, h * 0.5));
                g2.fill(new Ellipse2D.Double(w * 0.38, h * 0.05, w * 0.5, h * 0.5));
                g2.setColor(new Color(60, 160, 70));
                g2.fill(new Ellipse2D.Double(w * 0.25, h * 0.2, w * 0.5, h * 0.45));
                g2.setColor(new Color(235, 245, 235));
                g2.fill(new Ellipse2D.Double(w * 0.42, h * 0.38, w * 0.16, h * 0.16));
                g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(w * 0.5, h * 0.55, w * 0.5, h * 0.9));
            }
            g2.dispose();
        }
    }

    // ---- Sidebar button ----------------------------------------------
    private static class NavItem extends JButton {

        private final String label;
        private final boolean selected;

        NavItem(String label, boolean selected) {
            this.label = label;
            this.selected = selected;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(200, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int w = getWidth();
            int h = getHeight();

            if (selected) {
                g2.setColor(new Color(255, 255, 255, 45));
                g2.fill(new RoundRectangle2D.Double(0, 1, w, h - 2, 16, 16));
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 24));
                g2.fill(new RoundRectangle2D.Double(0, 1, w, h - 2, 16, 16));
            }

            // small icon box with the first letter
            g2.setColor(new Color(255, 255, 255, selected ? 60 : 32));
            g2.fill(new RoundRectangle2D.Double(14, (h - 24) / 2.0, 24, 24, 8, 8));
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            String letter = label.substring(0, 1);
            g2.drawString(letter, 14 + (24 - fm.stringWidth(letter)) / 2,
                    (h - fm.getHeight()) / 2 + fm.getAscent());

            g2.setFont(f(selected ? Font.BOLD : Font.PLAIN, 14));
            fm = g2.getFontMetrics();
            g2.drawString(label, 50, (h - fm.getHeight()) / 2 + fm.getAscent());

            g2.dispose();
        }
    }

    // ---- Notification bell -------------------------------------------
    private static class BellButton extends JComponent {

        int badge = 0;

        BellButton() {
            setPreferredSize(new Dimension(34, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setBadge(int n) {
            badge = n;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            g2.setColor(GREEN_BTN);
            g2.fill(new Arc2D.Double(8, 5, 16, 18, 0, 180, Arc2D.CHORD));
            g2.fill(new RoundRectangle2D.Double(8, 13, 16, 9, 3, 3));
            g2.fill(new Rectangle2D.Double(6, 20, 20, 3));
            g2.fill(new Ellipse2D.Double(13, 24, 6, 5));

            if (badge > 0) {
                g2.setColor(new Color(220, 50, 50));
                g2.fill(new Ellipse2D.Double(18, 0, 15, 15));
                g2.setColor(Color.WHITE);
                g2.setFont(f(Font.BOLD, 9));
                String s = badge > 9 ? "9+" : String.valueOf(badge);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s, 18 + (15 - fm.stringWidth(s)) / 2, 11);
            }
            g2.dispose();
        }
    }

    // ---- Round avatar with the user's initial ------------------------
    private static class Avatar extends JComponent {

        private final String initial;

        Avatar(String initial) {
            this.initial = initial;
            setPreferredSize(new Dimension(38, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);
            g2.setPaint(new GradientPaint(0, 0, new Color(60, 160, 90),
                    38, 38, new Color(14, 100, 62)));
            g2.fill(new Ellipse2D.Double(0, 0, 38, 38));
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 17));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, (38 - fm.stringWidth(initial)) / 2,
                    (38 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // ---- White / coloured rounded card with soft shadow --------------
    private static class RoundedCard extends JPanel {

        private final Color fill;

        RoundedCard(Color fill) {
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int w = getWidth();
            int h = getHeight();

            for (int i = 0; i < 4; i++) {
                g2.setColor(new Color(20, 70, 45, 11));
                g2.fill(new RoundRectangle2D.Double(
                    3 - i, 4 - i, w - 6 + 2 * i, h - 7 + 2 * i, 22 + i * 2, 22 + i * 2));
            }

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(3, 1, w - 6, h - 7, 20, 20));
            g2.dispose();
        }
    }

    // ---- Rounded button ----------------------------------------------
    private static class RoundButton extends JButton {

        RoundButton(String text, Color bg, Color fg) {
            super(text);
            setBackground(bg);
            setForeground(fg);
            setFont(f(Font.BOLD, 13));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            Color c = getBackground();
            if (getModel().isPressed()) {
                c = c.darker().darker();
            } else if (getModel().isRollover()) {
                c = c.darker();
            }

            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // ---- Round icon on the stat cards --------------------------------
    private static class IconCircle extends JComponent {

        private final Color color;
        private final int kind;

        IconCircle(Color color, int kind) {
            this.color = color;
            this.kind = kind;
            setPreferredSize(new Dimension(44, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(0, 0, 44, 44));
            g2.setColor(Color.WHITE);

            switch (kind) {
                case 0: // people
                    g2.fill(new Ellipse2D.Double(18, 10, 9, 9));
                    g2.fill(new Arc2D.Double(14, 20, 17, 16, 0, 180, Arc2D.CHORD));
                    g2.fill(new Ellipse2D.Double(8, 14, 7, 7));
                    g2.fill(new Ellipse2D.Double(29, 14, 7, 7));
                    break;
                case 1: // document
                    g2.fill(new RoundRectangle2D.Double(13, 9, 18, 26, 4, 4));
                    g2.setColor(color);
                    g2.fill(new Rectangle2D.Double(17, 16, 10, 2));
                    g2.fill(new Rectangle2D.Double(17, 21, 10, 2));
                    g2.fill(new Rectangle2D.Double(17, 26, 7, 2));
                    break;
                case 2: // check mark
                    g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    g2.draw(new Line2D.Double(12, 23, 19, 30));
                    g2.draw(new Line2D.Double(19, 30, 32, 14));
                    break;
                default: // star
                    Path2D star = new Path2D.Double();
                    for (int i = 0; i < 10; i++) {
                        double a = -Math.PI / 2 + i * Math.PI / 5;
                        double r = (i % 2 == 0) ? 13 : 6;
                        double x = 22 + r * Math.cos(a);
                        double y = 23 + r * Math.sin(a);
                        if (i == 0) {
                            star.moveTo(x, y);
                        } else {
                            star.lineTo(x, y);
                        }
                    }
                    star.closePath();
                    g2.fill(star);
                    break;
            }
            g2.dispose();
        }
    }

    // ---- Welcome banner ----------------------------------------------
    private class BannerPanel extends JComponent {

        BannerPanel() {
            setPreferredSize(new Dimension(100, 150));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int w = getWidth();
            int h = getHeight();

            g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, 24, 24));

            if (bannerImage != null) {
                drawCover(g2, bannerImage, w, h);
                g2.setPaint(new GradientPaint(0, 0, new Color(0, 40, 25, 190),
                        w * 0.65f, 0, new Color(0, 40, 25, 0)));
                g2.fillRect(0, 0, w, h);
            } else {
                paintScene(g2, w, h);
            }

            // texts
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 30));
            g2.drawString("Welcome back, " + fullName + "!", 34, h / 2 - 6);

            g2.setFont(f(Font.ITALIC, 17));
            g2.drawString(bannerSubtitle(), 34, h / 2 + 26);

            String[] quote = bannerQuote();
            g2.setFont(f(Font.ITALIC, 17));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(quote[0], w - 30 - fm.stringWidth(quote[0]), h / 2 + 2);
            g2.drawString(quote[1], w - 30 - fm.stringWidth(quote[1]), h / 2 + 26);

            g2.dispose();
        }

        // Drawn scene used when no banner.jpg is provided
        private void paintScene(Graphics2D g2, int w, int h) {

            g2.setPaint(new GradientPaint(0, 0, new Color(18, 80, 78),
                    0, h, new Color(236, 188, 96)));
            g2.fillRect(0, 0, w, h);

            // sun glow
            float cx = (float) (w * 0.66);
            float cy = (float) (h * 0.66);
            g2.setPaint(new RadialGradientPaint(cx, cy, h * 0.9f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 240, 160, 235), new Color(255, 240, 160, 0)}));
            g2.fillRect(0, 0, w, h);

            // far hills
            Path2D far = new Path2D.Double();
            far.moveTo(0, h * 0.75);
            far.curveTo(w * 0.2, h * 0.55, w * 0.4, h * 0.85, w * 0.6, h * 0.68);
            far.curveTo(w * 0.8, h * 0.55, w * 0.9, h * 0.7, w, h * 0.62);
            far.lineTo(w, h);
            far.lineTo(0, h);
            far.closePath();
            g2.setColor(new Color(58, 130, 72));
            g2.fill(far);

            // near hills
            Path2D near = new Path2D.Double();
            near.moveTo(0, h * 0.9);
            near.curveTo(w * 0.25, h * 0.75, w * 0.5, h * 0.98, w * 0.75, h * 0.82);
            near.curveTo(w * 0.9, h * 0.74, w * 0.95, h * 0.85, w, h * 0.8);
            near.lineTo(w, h);
            near.lineTo(0, h);
            near.closePath();
            g2.setColor(new Color(28, 98, 54));
            g2.fill(near);

            // person with raised arms
            double px = w * 0.66;
            double py = h * 0.80;
            g2.setColor(new Color(22, 48, 34));
            g2.fill(new Ellipse2D.Double(px - 6, py - 52, 12, 12));
            g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(px, py - 40, px, py - 14));
            g2.draw(new Line2D.Double(px, py - 14, px - 8, py));
            g2.draw(new Line2D.Double(px, py - 14, px + 8, py));
            g2.draw(new Line2D.Double(px, py - 36, px - 16, py - 54));
            g2.draw(new Line2D.Double(px, py - 36, px + 16, py - 54));

            // birds
            g2.setColor(new Color(40, 60, 50));
            g2.setStroke(new BasicStroke(2f));
            double[][] birds = {{0.58, 0.28}, {0.63, 0.2}, {0.70, 0.3}, {0.75, 0.18}};
            for (double[] b : birds) {
                double bx = w * b[0];
                double by = h * b[1];
                g2.draw(new Arc2D.Double(bx - 8, by - 4, 8, 8, 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(bx, by - 4, 8, 8, 0, 180, Arc2D.OPEN));
            }
        }
    }

    // ---- Growth tree picture (5 images only) -------------------------
    private class TreePanel extends JComponent {

        private int stage = 0;

        TreePanel() {
            setPreferredSize(new Dimension(200, 200));
        }

        void setStage(int s) {
            // 5 images = index 0 to 4, so stage 5 also uses the last image
            stage = Math.max(0, Math.min(4, s));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int w = getWidth();
            int h = getHeight();

            // soft background
            g2.setPaint(new GradientPaint(0, 0, new Color(232, 246, 236),
                    0, h, new Color(250, 252, 240)));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 18, 18));

            if (treeImages[stage] != null) {
                drawFit(g2, treeImages[stage], w, h);
            } else {
                g2.setColor(MUTED);
                g2.setFont(f(Font.PLAIN, 12));
                String msg = "tree_" + stage + ".png not found";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            }
            g2.dispose();
        }
    }

    // ---- One row of the growth checklist -----------------------------
    private static class MilestoneRow extends JComponent {

        private final String text;
        private boolean done = false;

        MilestoneRow(String text) {
            this.text = text;
            setPreferredSize(new Dimension(220, 28));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setDone(boolean d) {
            done = d;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            int y = (getHeight() - 22) / 2;

            if (done) {
                g2.setColor(new Color(34, 150, 84));
                g2.fill(new Ellipse2D.Double(2, y, 22, 22));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(8, y + 11, 12, y + 15));
                g2.draw(new Line2D.Double(12, y + 15, 19, y + 7));
            } else {
                g2.setColor(new Color(175, 185, 180));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(3, y + 1, 20, 20));
            }

            g2.setColor(done ? TEXT : MUTED);
            g2.setFont(f(Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 34, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // ---- One opportunity mini-card -----------------------------------
    private class OppCard extends JPanel {

        private final Color accent;

        OppCard(String[] data, int index) {

            Color[] accents = {
                new Color(46, 120, 150), new Color(214, 150, 40),
                new Color(100, 90, 170)
            };
            accent = accents[index % accents.length];

            setLayout(new BorderLayout(0, 6));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            JComponent top = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    setQuality(g2);
                    g2.setPaint(new GradientPaint(0, 0, accent,
                            getWidth(), getHeight(), accent.darker()));
                    g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(),
                            getHeight(), 14, 14));
                    g2.setColor(Color.WHITE);
                    g2.setFont(f(Font.BOLD, 15));
                    String type = data[2].isEmpty() ? "Opportunity" : data[2];
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(type, (getWidth() - fm.stringWidth(type)) / 2,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            top.setPreferredSize(new Dimension(100, 76));

            JLabel title = new JLabel("<html><b>" + esc(data[0]) + "</b></html>");
            title.setFont(f(Font.BOLD, 14));
            title.setForeground(TEXT);

            JLabel org = new JLabel("<html>" + esc(data[1]) + "</html>");
            org.setFont(f(Font.PLAIN, 12));
            org.setForeground(MUTED);

            JPanel texts = new JPanel(new GridLayout(2, 1, 0, 2));
            texts.setOpaque(false);
            texts.add(title);
            texts.add(org);

            RoundButton view = new RoundButton("View Opportunity",
                    GREEN_BTN, Color.WHITE);
            view.setFont(f(Font.BOLD, 12));
            view.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            view.addActionListener(e -> openModule("Opportunities"));

            add(top, BorderLayout.NORTH);
            add(texts, BorderLayout.CENTER);
            add(view, BorderLayout.SOUTH);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- One "needs attention" row (counsellor) ----------------------
    private class AttentionRow extends JPanel {

        AttentionRow(String name, String reason) {

            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 10));

            JLabel text = new JLabel("<html><b>" + esc(name) + "</b><br>"
                + "<span style='color:#B4501E'>" + esc(reason) + "</span></html>");
            text.setFont(f(Font.PLAIN, 13));
            text.setForeground(TEXT);

            RoundButton b = new RoundButton("Schedule", GREEN_BTN, Color.WHITE);
            b.setFont(f(Font.BOLD, 12));
            b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            b.addActionListener(e -> openModule("Schedule Session"));

            add(text, BorderLayout.CENTER);
            add(b, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);
            g2.setColor(new Color(253, 243, 236));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---- Heart icon on the support card ------------------------------
    private static class HeartIcon extends JComponent {

        HeartIcon() {
            setPreferredSize(new Dimension(120, 100));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            setQuality(g2);

            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0 - 4;

            // hands
            g2.setColor(new Color(244, 176, 120));
            g2.fill(new RoundRectangle2D.Double(cx - 46, cy + 18, 44, 22, 20, 20));
            g2.fill(new RoundRectangle2D.Double(cx + 2, cy + 18, 44, 22, 20, 20));

            // heart
            Area heart = new Area(new Ellipse2D.Double(cx - 24, cy - 22, 26, 26));
            heart.add(new Area(new Ellipse2D.Double(cx - 2, cy - 22, 26, 26)));
            Path2D tri = new Path2D.Double();
            tri.moveTo(cx - 22, cy - 5);
            tri.lineTo(cx + 22, cy - 5);
            tri.lineTo(cx, cy + 24);
            tri.closePath();
            heart.add(new Area(tri));

            g2.setColor(new Color(224, 52, 62));
            g2.fill(heart);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Quick test (normally LoginForm creates this window)
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new RoleBasedDashboard(1, "Arun Kumar", "Prisoner").setVisible(true));
    }
}