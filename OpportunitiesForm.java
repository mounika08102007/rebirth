package rebirth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/**
 * REbirth - Job and Training Opportunities
 *
 * Cards with deadline countdown, type filter chips and live search.
 *   Prisoner     : "Apply Now"  -> saved in OPPORTUNITY_APPLICATIONS
 *                  (this is what makes the Growth Tree grow)
 *   NGO / Admin  : add, edit, close opportunities, see applicants and
 *                  mark an application as Completed
 */
public class OpportunitiesForm extends JFrame {

    private final int userId;
    private final String fullName;
    private final String role;
    private final boolean manager;
    private final boolean prisoner;

    private static class Opp {
        int id = -1;
        String title = "", type = "", skills = "", location = "";
        String education = "", org = "", website = "", contact = "";
        String status = "Active", description = "", eligibility = "";
        LocalDate deadline = null;
    }

    private static final DateTimeFormatter SHOW =
        DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final List<Opp> all = new ArrayList<>();
    private final Map<Integer, OppCard> cards = new HashMap<>();
    private String activeType = "All";
    private int selectedId = -1;

    private JTextField searchField;
    private JPanel chipRow;
    private JPanel grid;
    private JPanel detailBody;
    private Theme.Badge countBadge;

    // ---- skill match (prisoner only) ----
    private final java.util.Set<String> mySkills = new java.util.HashSet<>();

    private void loadMySkills() {
        if (!prisoner) {
            return;
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT SKILLS FROM PRISONER_DETAILS WHERE USER_ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    mySkills.addAll(splitSkills(rs.getString(1)));
                }
            }
        } catch (SQLException ex) {
            // no profile skills: simply no match badges
        }
    }

    private static List<String> splitSkills(String text) {
        List<String> out = new ArrayList<>();
        for (String t : Theme.nz(text).toLowerCase()
                .split("[,;/&|\\n]|\\band\\b")) {
            t = t.trim();
            if (t.length() > 1) {
                out.add(t);
            }
        }
        return out;
    }

    /** -1 = no badge, otherwise 0..100 */
    private int matchPercent(Opp o) {
        if (!prisoner || mySkills.isEmpty()) {
            return -1;
        }
        List<String> need = splitSkills(o.skills);
        if (need.isEmpty()) {
            return -1;
        }
        int hit = 0;
        for (String n : need) {
            for (String m : mySkills) {
                if (n.equals(m)
                        || (m.length() >= 3 && n.contains(m))
                        || (n.length() >= 3 && m.contains(n))) {
                    hit++;
                    break;
                }
            }
        }
        return hit * 100 / need.size();
    }

    private Theme.Badge matchBadge(Opp o) {
        int p = matchPercent(o);
        if (p < 0) {
            return null;
        }
        Color c = p >= 67 ? Theme.GREEN
                : p >= 34 ? Theme.ORANGE : new Color(130, 140, 135);
        return new Theme.Badge(p + "% match", c, Color.WHITE);
    }

    public OpportunitiesForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;

        manager = role != null
            && (role.equalsIgnoreCase("NGO")
                || role.equalsIgnoreCase("Admin")
                || role.equalsIgnoreCase("Administrator"));
        prisoner = role != null && role.equalsIgnoreCase("Prisoner");

        setTitle("REbirth - Opportunities");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1050, 650));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE_BG);

        Theme.HeaderBand band = new Theme.HeaderBand(
            "Job & Training Opportunities",
            "Find work, learn new skills and take your next step");

        countBadge = new Theme.Badge("0 open",
            new Color(255, 255, 255, 50), Color.WHITE);
        band.addRight(countBadge);

        if (manager) {
            Theme.Button add = new Theme.Button(
                "+ New Opportunity", Color.WHITE, Theme.GREEN);
            add.addActionListener(e -> openEditor(-1));
            band.addRight(add);
        }
        root.add(band, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(16, 26, 20, 26));

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(buildToolbar(), BorderLayout.NORTH);

        grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(false);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(grid, BorderLayout.NORTH);

        JScrollPane gridScroll = new JScrollPane(wrap);
        gridScroll.setBorder(null);
        gridScroll.setOpaque(false);
        gridScroll.getViewport().setOpaque(false);
        gridScroll.getVerticalScrollBar().setUnitIncrement(22);
        gridScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        left.add(gridScroll, BorderLayout.CENTER);

        Theme.Card detailCard = new Theme.Card(Color.WHITE);
        detailCard.setLayout(new BorderLayout());
        detailCard.setBorder(BorderFactory.createEmptyBorder(10, 8, 14, 8));
        detailCard.setPreferredSize(new Dimension(450, 100));

        detailBody = new JPanel();
        detailBody.setOpaque(false);
        detailBody.setLayout(new BoxLayout(detailBody, BoxLayout.Y_AXIS));
        detailBody.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));

        JPanel detailWrap = new JPanel(new BorderLayout());
        detailWrap.setOpaque(false);
        detailWrap.add(detailBody, BorderLayout.NORTH);

        JScrollPane detailScroll = new JScrollPane(detailWrap);
        detailScroll.setBorder(null);
        detailScroll.setOpaque(false);
        detailScroll.getViewport().setOpaque(false);
        detailScroll.getVerticalScrollBar().setUnitIncrement(18);
        detailScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        detailCard.add(detailScroll, BorderLayout.CENTER);

        body.add(left, BorderLayout.CENTER);
        body.add(detailCard, BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        showEmptyDetail();
        loadMySkills();
        loadOpportunities();
    }

    // ------------------------------------------------------------------
    // Search + chips
    // ------------------------------------------------------------------
    private JPanel buildToolbar() {

        JPanel bar = new JPanel(new BorderLayout(0, 10));
        bar.setOpaque(false);

        searchField = Theme.field();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JLabel hint = new JLabel(
            "Search by title, skill, location, qualification or organization");
        hint.setFont(Theme.f(Font.BOLD, 12));
        hint.setForeground(Theme.MUTED);

        JPanel searchBox = new JPanel(new BorderLayout(0, 4));
        searchBox.setOpaque(false);
        searchBox.add(hint, BorderLayout.NORTH);
        searchBox.add(searchField, BorderLayout.CENTER);

        chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        chipRow.setOpaque(false);

        bar.add(searchBox, BorderLayout.NORTH);
        bar.add(chipRow, BorderLayout.CENTER);
        return bar;
    }

    private void rebuildChips() {

        chipRow.removeAll();
        ButtonGroup group = new ButtonGroup();

        TreeSet<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Opp o : all) {
            if (!o.type.trim().isEmpty()) {
                types.add(o.type.trim());
            }
        }

        List<String> names = new ArrayList<>();
        names.add("All");
        names.addAll(types);

        boolean found = false;
        for (String n : names) {
            if (n.equalsIgnoreCase(activeType)) {
                found = true;
            }
        }
        if (!found) {
            activeType = "All";
        }

        for (String n : names) {
            Theme.Chip chip = new Theme.Chip(n);
            chip.setSelected(n.equalsIgnoreCase(activeType));
            chip.addActionListener(e -> {
                activeType = n;
                applyFilter();
            });
            group.add(chip);
            chipRow.add(chip);
        }
        chipRow.revalidate();
        chipRow.repaint();
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------
    private static boolean isActive(String status) {
        return status != null && status.trim().equalsIgnoreCase("ACTIVE");
    }

    private static boolean expired(Opp o) {
        return o.deadline != null && o.deadline.isBefore(LocalDate.now());
    }

    private void loadOpportunities() {

        all.clear();

        String sql =
            "SELECT OPPORTUNITY_ID, OPPORTUNITY_TITLE, OPPORTUNITY_TYPE, "
            + "REQUIRED_SKILL, LOCATION, EDUCATION_QUALIFICATION, "
            + "ORGANIZATION_NAME, APPLICATION_DEADLINE, WEBSITE_URL, "
            + "CONTACT_DETAILS, STATUS FROM OPPORTUNITIES WHERE 1=1 ";

        if (!manager) {
            sql += "AND UPPER(STATUS) = 'ACTIVE' "
                + "AND (APPLICATION_DEADLINE IS NULL "
                + "OR APPLICATION_DEADLINE >= TRUNC(SYSDATE)) ";
        }
        sql += "ORDER BY OPPORTUNITY_ID DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Opp o = new Opp();
                o.id = rs.getInt("OPPORTUNITY_ID");
                o.title = Theme.nz(rs.getString("OPPORTUNITY_TITLE"));
                o.type = Theme.nz(rs.getString("OPPORTUNITY_TYPE"));
                o.skills = Theme.nz(rs.getString("REQUIRED_SKILL"));
                o.location = Theme.nz(rs.getString("LOCATION"));
                o.education = Theme.nz(rs.getString("EDUCATION_QUALIFICATION"));
                o.org = Theme.nz(rs.getString("ORGANIZATION_NAME"));
                java.sql.Date dt = rs.getDate("APPLICATION_DEADLINE");
                o.deadline = dt == null ? null : dt.toLocalDate();
                o.website = Theme.nz(rs.getString("WEBSITE_URL"));
                o.contact = Theme.nz(rs.getString("CONTACT_DETAILS"));
                o.status = Theme.nz(rs.getString("STATUS"));
                all.add(o);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load opportunities:\n" + ex.getMessage());
        }

        int open = 0;
        for (Opp o : all) {
            if (isActive(o.status) && !expired(o)) {
                open++;
            }
        }
        countBadge.setText(open + " open");

        rebuildChips();
        applyFilter();
    }

    private void applyFilter() {

        String q = searchField.getText().trim().toLowerCase();

        grid.removeAll();
        cards.clear();
        int shown = 0;

        List<Opp> ordered = new ArrayList<>(all);
        if (prisoner && !mySkills.isEmpty()) {
            // best skill matches first
            ordered.sort((a, b) -> Integer.compare(matchPercent(b), matchPercent(a)));
        }

        for (Opp o : ordered) {

            if (!activeType.equals("All")
                    && !activeType.equalsIgnoreCase(o.type.trim())) {
                continue;
            }

            if (!q.isEmpty()) {
                String hay = (o.title + " " + o.type + " " + o.skills + " "
                    + o.location + " " + o.education + " " + o.org).toLowerCase();
                if (!hay.contains(q)) {
                    continue;
                }
            }

            OppCard card = new OppCard(o);
            card.setHighlight(o.id == selectedId);
            cards.put(o.id, card);
            grid.add(card);
            shown++;
        }

        if (shown == 0) {
            JLabel none = new JLabel("No opportunities match your search.");
            none.setFont(Theme.f(Font.PLAIN, 15));
            none.setForeground(Theme.MUTED);
            none.setBorder(BorderFactory.createEmptyBorder(30, 10, 0, 0));
            grid.add(none);
        }

        grid.revalidate();
        grid.repaint();
    }

    private void select(Opp o) {

        selectedId = o.id;
        for (Map.Entry<Integer, OppCard> e : cards.entrySet()) {
            e.getValue().setHighlight(e.getKey() == selectedId);
        }

        Opp full = fetchFull(o.id);
        if (full != null) {
            showDetail(full);
        }
    }

    private Opp fetchFull(int id) {

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT * FROM OPPORTUNITIES WHERE OPPORTUNITY_ID = ?")) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Opp o = new Opp();
                    o.id = id;
                    o.title = Theme.nz(rs.getString("OPPORTUNITY_TITLE"));
                    o.type = Theme.nz(rs.getString("OPPORTUNITY_TYPE"));
                    o.skills = Theme.nz(rs.getString("REQUIRED_SKILL"));
                    o.location = Theme.nz(rs.getString("LOCATION"));
                    o.education = Theme.nz(rs.getString("EDUCATION_QUALIFICATION"));
                    o.org = Theme.nz(rs.getString("ORGANIZATION_NAME"));
                    java.sql.Date dt = rs.getDate("APPLICATION_DEADLINE");
                    o.deadline = dt == null ? null : dt.toLocalDate();
                    o.website = Theme.nz(rs.getString("WEBSITE_URL"));
                    o.contact = Theme.nz(rs.getString("CONTACT_DETAILS"));
                    o.description = Theme.nz(rs.getString("DESCRIPTION"));
                    o.eligibility = Theme.nz(rs.getString("ELIGIBILITY_CRITERIA"));
                    o.status = Theme.nz(rs.getString("STATUS"));
                    return o;
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load details:\n" + ex.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Deadline helpers
    // ------------------------------------------------------------------
    private static Theme.Badge deadlineBadge(Opp o) {

        if (!isActive(o.status)) {
            return new Theme.Badge("Closed", new Color(236, 236, 236), Theme.MUTED);
        }
        if (o.deadline == null) {
            return new Theme.Badge("Open", Theme.GREEN_SOFT, Theme.GREEN);
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), o.deadline);

        if (days < 0) {
            return new Theme.Badge("Expired", new Color(236, 236, 236), Theme.MUTED);
        }
        if (days == 0) {
            return new Theme.Badge("Closes today",
                new Color(252, 226, 226), Theme.RED);
        }
        if (days <= 7) {
            return new Theme.Badge(days + (days == 1 ? " day left" : " days left"),
                new Color(255, 236, 210), Theme.ORANGE);
        }
        return new Theme.Badge(days + " days left", Theme.GREEN_SOFT, Theme.GREEN);
    }

    // ------------------------------------------------------------------
    // Detail panel
    // ------------------------------------------------------------------
    private void showEmptyDetail() {

        detailBody.removeAll();
        detailBody.add(Box.createVerticalStrut(120));

        JLabel big = new JLabel("Select an opportunity");
        big.setFont(Theme.f(Font.BOLD, 20));
        big.setForeground(Theme.GREEN);
        big.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailBody.add(big);
        detailBody.add(Box.createVerticalStrut(8));
        detailBody.add(Theme.text(prisoner
            ? "Click a card to see the requirements and apply. "
              + "Every step you take helps your tree grow."
            : "Click a card to see its details and manage applications.",
            340, 14, Theme.MUTED));

        detailBody.revalidate();
        detailBody.repaint();
    }

    private JLabel sectionTitle(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.f(Font.BOLD, 14));
        l.setForeground(Theme.GREEN);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(16, 0, 6, 0));
        return l;
    }

    private void showDetail(Opp o) {

        detailBody.removeAll();

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!o.type.trim().isEmpty()) {
            badges.add(new Theme.Badge(o.type, Theme.accentFor(o.type), Color.WHITE));
        }
        Theme.Badge detailMatch = matchBadge(o);
        if (detailMatch != null) {
            badges.add(detailMatch);
        }
        badges.add(deadlineBadge(o));
        badges.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        detailBody.add(badges);
        detailBody.add(Box.createVerticalStrut(10));

        JLabel title = new JLabel("<html><body style='width:340px'><b>"
            + Theme.esc(o.title) + "</b></body></html>");
        title.setFont(Theme.f(Font.BOLD, 21));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailBody.add(title);

        String sub = o.org;
        if (!o.location.trim().isEmpty()) {
            sub += (sub.isEmpty() ? "" : "  |  ") + o.location;
        }
        if (!sub.isEmpty()) {
            detailBody.add(Box.createVerticalStrut(4));
            detailBody.add(Theme.text(sub, 340, 13, Theme.MUTED));
        }

        if (o.deadline != null) {
            detailBody.add(sectionTitle("Apply before"));
            detailBody.add(Theme.text(o.deadline.format(SHOW), 340, 14, Theme.TEXT));
        }

        if (!o.description.trim().isEmpty()) {
            detailBody.add(sectionTitle("About this opportunity"));
            detailBody.add(Theme.text(o.description, 340, 13, Theme.TEXT));
        }

        List<String> elig = split(o.eligibility);
        if (!elig.isEmpty()) {
            detailBody.add(sectionTitle("Eligibility"));
            for (String s : elig) {
                detailBody.add(Theme.text("\u2022  " + s, 340, 13, Theme.TEXT));
                detailBody.add(Box.createVerticalStrut(3));
            }
        }

        if (!o.skills.trim().isEmpty()) {
            detailBody.add(sectionTitle("Skills required"));
            detailBody.add(Theme.text(o.skills, 340, 13, Theme.TEXT));
        }

        if (!o.education.trim().isEmpty()) {
            detailBody.add(sectionTitle("Education"));
            detailBody.add(Theme.text(o.education, 340, 13, Theme.TEXT));
        }

        if (!o.contact.trim().isEmpty()) {
            detailBody.add(sectionTitle("Contact"));
            detailBody.add(Theme.text(o.contact, 340, 13, Theme.TEXT));
        }

        detailBody.add(Box.createVerticalStrut(18));

        // ---- prisoner: apply ----
        if (prisoner) {
            String applied = applicationStatus(o.id);
            boolean open = isActive(o.status) && !expired(o);

            Theme.Button apply;
            if (applied != null) {
                apply = new Theme.Button("Applied  (" + applied + ")",
                    Theme.GREEN, Color.WHITE);
                apply.setEnabled(false);
            } else {
                apply = new Theme.Button(open ? "Apply Now" : "Applications closed",
                    Theme.GREEN, Color.WHITE);
                apply.setEnabled(open);
                apply.addActionListener(e -> applyFor(o));
            }
            detailBody.add(apply);
            detailBody.add(Box.createVerticalStrut(8));
        }

        if (!o.website.trim().isEmpty()) {
            Theme.Button web = new Theme.Button("Open Website",
                new Color(236, 242, 238), Theme.TEXT);
            web.addActionListener(e -> Theme.openUrl(this, o.website));
            detailBody.add(web);
            detailBody.add(Box.createVerticalStrut(8));
        }

        // ---- manager tools ----
        if (manager) {
            Theme.Button applicants = new Theme.Button(
                "View Applicants", Theme.GREEN, Color.WHITE);
            applicants.addActionListener(e -> showApplicants(o));
            detailBody.add(applicants);
            detailBody.add(Box.createVerticalStrut(8));

            Theme.Button edit = new Theme.Button("Edit",
                new Color(236, 242, 238), Theme.TEXT);
            edit.addActionListener(e -> openEditor(o.id));
            detailBody.add(edit);

            if (isActive(o.status)) {
                detailBody.add(Box.createVerticalStrut(8));
                Theme.Button close = new Theme.Button("Close Opportunity",
                    new Color(252, 226, 226), Theme.RED);
                close.addActionListener(e -> closeOpportunity(o));
                detailBody.add(close);
            }
        }

        detailBody.revalidate();
        detailBody.repaint();
    }

    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
        String[] parts = text.replace("\r", "").split("\\n|;");
        for (String p : parts) {
            String s = p.trim().replaceFirst("^(\\d+[.)]|[-*\\u2022])\\s*", "");
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Applications (prisoner)
    // ------------------------------------------------------------------
    // Creates the applications table the first time it is needed
    private static void ensureApplicationsTable(Connection con) {

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT 1 FROM OPPORTUNITY_APPLICATIONS WHERE 1=0")) {
            return;
        } catch (SQLException missing) {
            // table does not exist yet, create it below
        }

        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE OPPORTUNITY_APPLICATIONS ("
                + "APPLICATION_ID NUMBER PRIMARY KEY, "
                + "PRISONER_ID NUMBER, "
                + "OPPORTUNITY_ID NUMBER, "
                + "APPLIED_DATE DATE DEFAULT SYSDATE, "
                + "STATUS VARCHAR2(20) DEFAULT 'Applied')");
        } catch (SQLException ignored) {
            // another window may have created it at the same time
        }
    }

    private int prisonerId() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT PRISONER_ID FROM PRISONER_DETAILS WHERE USER_ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // Returns the application status, or null if the prisoner has not applied
    private String applicationStatus(int opportunityId) {

        int pid = prisonerId();
        if (pid == 0) {
            return null;
        }

        try (Connection con = DBConnection.getConnection()) {
            ensureApplicationsTable(con);

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT STATUS FROM OPPORTUNITY_APPLICATIONS "
                    + "WHERE PRISONER_ID = ? AND OPPORTUNITY_ID = ?")) {
                ps.setInt(1, pid);
                ps.setInt(2, opportunityId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Theme.nz(rs.getString(1)) : null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private void applyFor(Opp o) {

        int pid = prisonerId();
        if (pid == 0) {
            JOptionPane.showMessageDialog(this, "Prisoner details were not found.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Apply for \"" + o.title + "\"?", "Confirm Application",
            JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection con = DBConnection.getConnection()) {

            ensureApplicationsTable(con);

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO OPPORTUNITY_APPLICATIONS "
                    + "(APPLICATION_ID, PRISONER_ID, OPPORTUNITY_ID, "
                    + "APPLIED_DATE, STATUS) VALUES ("
                    + "(SELECT NVL(MAX(APPLICATION_ID), 0) + 1 "
                    + "FROM OPPORTUNITY_APPLICATIONS), ?, ?, SYSDATE, 'Applied')")) {
                ps.setInt(1, pid);
                ps.setInt(2, o.id);
                ps.executeUpdate();
            }

            JOptionPane.showMessageDialog(this,
                "Application submitted! Your tree is growing.");

            Opp fresh = fetchFull(o.id);
            if (fresh != null) {
                showDetail(fresh);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Could not submit the application:\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Applicants (NGO / Admin)
    // ------------------------------------------------------------------
    private void showApplicants(Opp o) {

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"App ID", "Prisoner ID", "Name", "Applied On", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(Theme.f(Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(Theme.GREEN_SOFT);
        table.setSelectionForeground(Theme.TEXT);
        table.getTableHeader().setFont(Theme.f(Font.BOLD, 13));
        table.getTableHeader().setBackground(Theme.GREEN);
        table.getTableHeader().setForeground(Color.WHITE);

        loadApplicants(model, o.id);

        JDialog dlg = new JDialog(this, "Applicants - " + o.title, true);
        dlg.setSize(780, 460);
        dlg.setLocationRelativeTo(this);

        Theme.Button done = new Theme.Button(
            "Mark Completed", Theme.GREEN, Color.WHITE);
        Theme.Button undo = new Theme.Button(
            "Mark Applied", new Color(236, 242, 238), Theme.TEXT);
        Theme.Button close = new Theme.Button(
            "Close", new Color(236, 242, 238), Theme.TEXT);

        done.addActionListener(e -> {
            setApplicationStatus(table, model, o.id, "Completed");
        });
        undo.addActionListener(e -> {
            setApplicationStatus(table, model, o.id, "Applied");
        });
        close.addActionListener(e -> dlg.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Color.WHITE);
        buttons.add(undo);
        buttons.add(done);
        buttons.add(close);

        JLabel note = new JLabel(
            "  Completing an application moves that prisoner's Growth Tree forward.");
        note.setFont(Theme.f(Font.ITALIC, 12));
        note.setForeground(Theme.MUTED);
        note.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        dlg.setLayout(new BorderLayout());
        dlg.add(note, BorderLayout.NORTH);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void loadApplicants(DefaultTableModel model, int opportunityId) {

        model.setRowCount(0);

        try (Connection con = DBConnection.getConnection()) {

            ensureApplicationsTable(con);

            String sql =
                "SELECT a.APPLICATION_ID, a.PRISONER_ID, u.FULL_NAME, "
                + "TO_CHAR(a.APPLIED_DATE, 'DD-Mon-YYYY'), a.STATUS "
                + "FROM OPPORTUNITY_APPLICATIONS a "
                + "LEFT JOIN PRISONER_DETAILS p ON p.PRISONER_ID = a.PRISONER_ID "
                + "LEFT JOIN USERS u ON u.USER_ID = p.USER_ID "
                + "WHERE a.OPPORTUNITY_ID = ? ORDER BY a.APPLICATION_ID DESC";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, opportunityId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        model.addRow(new Object[]{
                            rs.getInt(1), rs.getInt(2), rs.getString(3),
                            rs.getString(4), rs.getString(5)
                        });
                    }
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load applicants:\n" + ex.getMessage());
        }
    }

    private void setApplicationStatus(JTable table, DefaultTableModel model,
                                      int opportunityId, String status) {

        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an applicant first.");
            return;
        }

        int appId = Integer.parseInt(model.getValueAt(row, 0).toString());

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE OPPORTUNITY_APPLICATIONS SET STATUS = ? "
                 + "WHERE APPLICATION_ID = ?")) {

            ps.setString(1, status);
            ps.setInt(2, appId);
            ps.executeUpdate();

            loadApplicants(model, opportunityId);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to update:\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Close an opportunity
    // ------------------------------------------------------------------
    private void closeOpportunity(Opp o) {

        if (!manager) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Close this opportunity?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE OPPORTUNITIES SET STATUS='Closed' WHERE OPPORTUNITY_ID=?")) {

            ps.setInt(1, o.id);
            ps.executeUpdate();

            selectedId = o.id;
            loadOpportunities();

            Opp fresh = fetchFull(o.id);
            if (fresh != null) {
                showDetail(fresh);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Editor dialog (NGO / Admin)
    // ------------------------------------------------------------------
    private void openEditor(int id) {

        if (!manager) {
            JOptionPane.showMessageDialog(this, "You cannot manage opportunities.");
            return;
        }

        Opp o = (id == -1) ? new Opp() : fetchFull(id);
        if (o == null) {
            return;
        }

        JDialog dlg = new JDialog(this,
            id == -1 ? "New Opportunity" : "Edit Opportunity", true);
        dlg.setSize(680, 800);
        dlg.setLocationRelativeTo(this);

        JTextField title = Theme.field();
        JTextField type = Theme.field();
        JTextField skills = Theme.field();
        JTextField location = Theme.field();
        JTextField education = Theme.field();
        JTextField org = Theme.field();
        JTextField deadline = Theme.field();
        JTextField website = Theme.field();
        JTextField contact = Theme.field();
        JComboBox<String> status = new JComboBox<>(new String[]{"Active", "Closed"});
        JTextArea description = Theme.area(4);
        JTextArea eligibility = Theme.area(3);

        title.setText(o.title);
        type.setText(o.type);
        skills.setText(o.skills);
        location.setText(o.location);
        education.setText(o.education);
        org.setText(o.org);
        deadline.setText(o.deadline == null ? "" : o.deadline.toString());
        website.setText(o.website);
        contact.setText(o.contact);
        description.setText(o.description);
        eligibility.setText(o.eligibility);
        status.setSelectedItem(isActive(o.status) ? "Active" : "Closed");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 20));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;

        int r = 0;
        addRow(form, g, r++, "Title *", title);
        addRow(form, g, r++, "Type * (e.g. Job, Course, Training)", type);
        addRow(form, g, r++, "Organization *", org);
        addRow(form, g, r++, "Required Skills", skills);
        addRow(form, g, r++, "Location", location);
        addRow(form, g, r++, "Education Qualification", education);
        addRow(form, g, r++, "Application Deadline * (YYYY-MM-DD)", deadline);
        addRow(form, g, r++, "Website", website);
        addRow(form, g, r++, "Contact Details", contact);
        addRow(form, g, r++, "Status", status);
        addRow(form, g, r++, "Description", Theme.scroll(description));
        addRow(form, g, r++, "Eligibility (one point per line)",
            Theme.scroll(eligibility));

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(18);

        Theme.Button save = new Theme.Button("Save", Theme.GREEN, Color.WHITE);
        Theme.Button cancel = new Theme.Button(
            "Cancel", new Color(236, 242, 238), Theme.TEXT);
        cancel.addActionListener(e -> dlg.dispose());

        save.addActionListener(e -> {

            if (title.getText().trim().isEmpty()
                    || type.getText().trim().isEmpty()
                    || org.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                    "Enter the title, type and organization.");
                return;
            }

            LocalDate date;
            try {
                date = LocalDate.parse(deadline.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg,
                    "Enter the deadline as YYYY-MM-DD (example: 2026-12-31).");
                return;
            }

            Opp n = new Opp();
            n.id = o.id;
            n.title = title.getText().trim();
            n.type = type.getText().trim();
            n.skills = skills.getText().trim();
            n.location = location.getText().trim();
            n.education = education.getText().trim();
            n.org = org.getText().trim();
            n.deadline = date;
            n.website = website.getText().trim();
            n.contact = contact.getText().trim();
            n.description = description.getText().trim();
            n.eligibility = eligibility.getText().trim();
            n.status = status.getSelectedItem().toString();

            if (saveOpportunity(n)) {
                dlg.dispose();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Color.WHITE);
        buttons.add(cancel);
        buttons.add(save);

        dlg.setLayout(new BorderLayout());
        dlg.add(formScroll, BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void addRow(JPanel p, GridBagConstraints g, int row,
                        String label, Component c) {

        JLabel l = new JLabel(label);
        l.setFont(Theme.f(Font.BOLD, 12));
        l.setForeground(Theme.MUTED);

        g.gridx = 0;
        g.gridy = row * 2;
        g.weightx = 1;
        p.add(l, g);

        g.gridy = row * 2 + 1;
        p.add(c, g);
    }

    // Same SQL as the old form
    private boolean saveOpportunity(Opp o) {

        boolean editing = o.id != -1;
        String sql;

        if (editing) {
            sql = "UPDATE OPPORTUNITIES SET "
                + "OPPORTUNITY_TITLE=?, OPPORTUNITY_TYPE=?, REQUIRED_SKILL=?, "
                + "LOCATION=?, EDUCATION_QUALIFICATION=?, ORGANIZATION_NAME=?, "
                + "APPLICATION_DEADLINE=TO_DATE(?, 'YYYY-MM-DD'), WEBSITE_URL=?, "
                + "CONTACT_DETAILS=?, DESCRIPTION=?, ELIGIBILITY_CRITERIA=?, STATUS=? "
                + "WHERE OPPORTUNITY_ID=?";
        } else {
            sql = "INSERT INTO OPPORTUNITIES "
                + "(OPPORTUNITY_ID, OPPORTUNITY_TITLE, OPPORTUNITY_TYPE, "
                + "REQUIRED_SKILL, LOCATION, EDUCATION_QUALIFICATION, "
                + "ORGANIZATION_NAME, APPLICATION_DEADLINE, WEBSITE_URL, "
                + "CONTACT_DETAILS, DESCRIPTION, ELIGIBILITY_CRITERIA, STATUS, "
                + "CREATED_DATE) "
                + "VALUES ((SELECT NVL(MAX(OPPORTUNITY_ID),0)+1 FROM OPPORTUNITIES), "
                + "?,?,?,?,?,?,TO_DATE(?, 'YYYY-MM-DD'),?,?,?,?,?,SYSDATE)";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, o.title);
            ps.setString(i++, o.type);
            ps.setString(i++, o.skills);
            ps.setString(i++, o.location);
            ps.setString(i++, o.education);
            ps.setString(i++, o.org);
            ps.setString(i++, o.deadline.toString());
            ps.setString(i++, o.website);
            ps.setString(i++, o.contact);
            ps.setString(i++, o.description);
            ps.setString(i++, o.eligibility);
            ps.setString(i++, o.status);

            if (editing) {
                ps.setInt(i, o.id);
            }

            ps.executeUpdate();

            if (editing) {
                selectedId = o.id;
            }
            loadOpportunities();

            if (editing) {
                Opp fresh = fetchFull(o.id);
                if (fresh != null) {
                    showDetail(fresh);
                }
            }

            JOptionPane.showMessageDialog(this,
                editing ? "Opportunity updated!" : "Opportunity added!");
            return true;

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to save:\n" + ex.getMessage());
            return false;
        }
    }

    // ==================================================================
    // Opportunity card
    // ==================================================================
    private class OppCard extends Theme.Card {

        private final Opp opp;
        private final Color accent;

        OppCard(Opp o) {
            super(Color.WHITE);
            this.opp = o;
            this.accent = Theme.accentFor(o.type);

            setLayout(new BorderLayout(0, 6));
            setBorder(BorderFactory.createEmptyBorder(14, 22, 16, 16));
            setPreferredSize(new Dimension(100, 165));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(new Theme.Badge(o.type.trim().isEmpty() ? "Opportunity" : o.type,
                accent, Color.WHITE));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            Theme.Badge mb = matchBadge(o);
            if (mb != null) {
                right.add(mb);
                right.add(Box.createHorizontalStrut(6));
            }
            right.add(deadlineBadge(o));

            top.add(left, BorderLayout.WEST);
            top.add(right, BorderLayout.EAST);

            JLabel name = new JLabel("<html><body style='width:230px'><b>"
                + Theme.esc(Theme.shorten(o.title, 60)) + "</b></body></html>");
            name.setFont(Theme.f(Font.BOLD, 15));
            name.setForeground(Theme.TEXT);

            String sub = o.org;
            if (!o.location.trim().isEmpty()) {
                sub += (sub.isEmpty() ? "" : "  |  ") + o.location;
            }
            JLabel org = new JLabel("<html><body style='width:230px'>"
                + Theme.esc(Theme.shorten(sub, 60)) + "</body></html>");
            org.setFont(Theme.f(Font.PLAIN, 12));
            org.setForeground(Theme.MUTED);

            JPanel mid = new JPanel(new BorderLayout(0, 3));
            mid.setOpaque(false);
            mid.add(name, BorderLayout.NORTH);
            mid.add(org, BorderLayout.CENTER);

            JLabel skills = new JLabel("<html><body style='width:230px'>"
                + (o.skills.trim().isEmpty() ? "" : "Skills: ")
                + Theme.esc(Theme.shorten(o.skills, 70)) + "</body></html>");
            skills.setFont(Theme.f(Font.PLAIN, 12));
            skills.setForeground(new Color(55, 75, 65));

            add(top, BorderLayout.NORTH);
            add(mid, BorderLayout.CENTER);
            add(skills, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    select(opp);
                }

                public void mouseEntered(MouseEvent e) {
                    setHover(true);
                }

                public void mouseExited(MouseEvent e) {
                    setHover(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Double(3, 1, 8, getHeight() - 7, 8, 8));
            g2.setColor(Color.WHITE);
            g2.fillRect(8, 1, 8, getHeight() - 7);
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new OpportunitiesForm(1, "Demo User", "Admin").setVisible(true));
    }
}
