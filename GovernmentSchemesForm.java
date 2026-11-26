package rebirth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * REbirth - Government Schemes
 *
 * Browse schemes as cards, filter by type, search instantly, and open a
 * detail panel with:
 *   - a step-by-step "How to apply" timeline
 *   - a "Documents checklist" with a readiness progress bar
 * Admin / NGO users can add and edit schemes (same database columns as before).
 */
public class GovernmentSchemesForm extends JFrame {

    private final int userId;
    private final String fullName;
    private final String role;
    private final boolean canManage;

    // short record used for the cards
    private static class Scheme {
        int id;
        String name, type, dept, eligibility, benefits, status;
    }

    // full record used for the detail panel and the editor
    private static class Detail {
        int id = -1;
        String name = "", type = "", dept = "", desc = "", elig = "";
        String benefits = "", docs = "", process = "", website = "";
        String contact = "", status = "ACTIVE";
    }

    private final List<Scheme> all = new ArrayList<>();
    private final Map<Integer, SchemeCard> cards = new HashMap<>();
    private String activeType = "All";
    private int selectedId = -1;

    private JTextField searchField;
    private JPanel chipRow;
    private JPanel grid;
    private JPanel detailBody;
    private Theme.Badge totalBadge;
    private Theme.Badge activeBadge;

    public GovernmentSchemesForm(int userId, String fullName, String role) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;

        canManage = role != null
            && (role.equalsIgnoreCase("ADMIN")
                || role.equalsIgnoreCase("ADMINISTRATOR")
                || role.equalsIgnoreCase("NGO"));

        setTitle("REbirth - Government Schemes");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1050, 650));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE_BG);

        // ---- header band ----
        Theme.HeaderBand band = new Theme.HeaderBand(
            "Government Schemes",
            "Benefits and support that help you build a better tomorrow");

        totalBadge = new Theme.Badge("0 schemes",
            new Color(255, 255, 255, 50), Color.WHITE);
        activeBadge = new Theme.Badge("0 active",
            new Color(255, 255, 255, 50), Color.WHITE);
        band.addRight(totalBadge);
        band.addRight(activeBadge);

        if (canManage) {
            Theme.Button add = new Theme.Button(
                "+ New Scheme", Color.WHITE, Theme.GREEN);
            add.addActionListener(e -> openEditor(-1));
            band.addRight(add);
        }
        root.add(band, BorderLayout.NORTH);

        // ---- body: cards on the left, details on the right ----
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
        loadSchemes();
    }

    // ------------------------------------------------------------------
    // Search box + type chips
    // ------------------------------------------------------------------
    private JPanel buildToolbar() {

        JPanel bar = new JPanel(new BorderLayout(0, 10));
        bar.setOpaque(false);

        searchField = Theme.field();
        searchField.setToolTipText("Search by name, type, department or benefit");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JLabel hint = new JLabel("Search schemes by name, type, department or benefit");
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
        for (Scheme s : all) {
            if (!Theme.nz(s.type).trim().isEmpty()) {
                types.add(s.type.trim());
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
    // Loading and filtering
    // ------------------------------------------------------------------
    private void loadSchemes() {

        all.clear();

        String sql =
            "SELECT SCHEME_ID, SCHEME_NAME, SCHEME_TYPE, DEPARTMENT_NAME, "
            + "ELIGIBILITY_CRITERIA, BENEFITS, STATUS FROM GOVERNMENT_SCHEMES ";

        if (!canManage) {
            sql += "WHERE UPPER(TRIM(STATUS)) = 'ACTIVE' ";
        }
        sql += "ORDER BY SCHEME_ID DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Scheme s = new Scheme();
                s.id = rs.getInt("SCHEME_ID");
                s.name = Theme.nz(rs.getString("SCHEME_NAME"));
                s.type = Theme.nz(rs.getString("SCHEME_TYPE"));
                s.dept = Theme.nz(rs.getString("DEPARTMENT_NAME"));
                s.eligibility = Theme.nz(rs.getString("ELIGIBILITY_CRITERIA"));
                s.benefits = Theme.nz(rs.getString("BENEFITS"));
                s.status = Theme.nz(rs.getString("STATUS"));
                all.add(s);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load schemes:\n" + ex.getMessage());
        }

        int active = 0;
        for (Scheme s : all) {
            if (isActive(s.status)) {
                active++;
            }
        }
        totalBadge.setText(all.size() + (all.size() == 1 ? " scheme" : " schemes"));
        activeBadge.setText(active + " active");

        rebuildChips();
        applyFilter();
    }

    private static boolean isActive(String status) {
        return status != null && status.trim().equalsIgnoreCase("ACTIVE");
    }

    private void applyFilter() {

        String q = searchField.getText().trim().toLowerCase();

        grid.removeAll();
        cards.clear();
        int shown = 0;

        for (Scheme s : all) {

            if (!activeType.equals("All")
                    && !activeType.equalsIgnoreCase(s.type.trim())) {
                continue;
            }

            if (!q.isEmpty()) {
                String hay = (s.name + " " + s.type + " " + s.dept + " "
                    + s.benefits + " " + s.eligibility).toLowerCase();
                if (!hay.contains(q)) {
                    continue;
                }
            }

            SchemeCard card = new SchemeCard(s);
            card.setHighlight(s.id == selectedId);
            cards.put(s.id, card);
            grid.add(card);
            shown++;
        }

        if (shown == 0) {
            JLabel none = new JLabel("No schemes match your search.");
            none.setFont(Theme.f(Font.PLAIN, 15));
            none.setForeground(Theme.MUTED);
            none.setBorder(BorderFactory.createEmptyBorder(30, 10, 0, 0));
            grid.add(none);
        }

        grid.revalidate();
        grid.repaint();
    }

    private void select(Scheme s) {

        selectedId = s.id;
        for (Map.Entry<Integer, SchemeCard> e : cards.entrySet()) {
            e.getValue().setHighlight(e.getKey() == selectedId);
        }

        Detail d = fetchDetail(s.id);
        if (d != null) {
            showDetail(d);
        }
    }

    // ------------------------------------------------------------------
    // Detail panel
    // ------------------------------------------------------------------
    private Detail fetchDetail(int id) {

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT * FROM GOVERNMENT_SCHEMES WHERE SCHEME_ID = ?")) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Detail d = new Detail();
                    d.id = id;
                    d.name = Theme.nz(rs.getString("SCHEME_NAME"));
                    d.type = Theme.nz(rs.getString("SCHEME_TYPE"));
                    d.dept = Theme.nz(rs.getString("DEPARTMENT_NAME"));
                    d.desc = Theme.nz(rs.getString("DESCRIPTION"));
                    d.elig = Theme.nz(rs.getString("ELIGIBILITY_CRITERIA"));
                    d.benefits = Theme.nz(rs.getString("BENEFITS"));
                    d.docs = Theme.nz(rs.getString("REQUIRED_DOCUMENTS"));
                    d.process = Theme.nz(rs.getString("APPLICATION_PROCESS"));
                    d.website = Theme.nz(rs.getString("OFFICIAL_WEBSITE"));
                    d.contact = Theme.nz(rs.getString("CONTACT_DETAILS"));
                    d.status = Theme.nz(rs.getString("STATUS"));
                    return d;
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load scheme details:\n" + ex.getMessage());
        }
        return null;
    }

    private void showEmptyDetail() {

        detailBody.removeAll();
        detailBody.add(Box.createVerticalStrut(120));

        JLabel big = new JLabel("Select a scheme");
        big.setFont(Theme.f(Font.BOLD, 20));
        big.setForeground(Theme.GREEN);
        big.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailBody.add(big);
        detailBody.add(Box.createVerticalStrut(8));
        detailBody.add(Theme.text(
            "Click any card on the left to see who can apply, what you get, "
            + "how to apply step by step, and which documents to keep ready.",
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

    private void showDetail(Detail d) {

        detailBody.removeAll();

        // badges
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!d.type.trim().isEmpty()) {
            badges.add(new Theme.Badge(d.type, Theme.accentFor(d.type), Color.WHITE));
        }
        badges.add(statusBadge(d.status));
        badges.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        detailBody.add(badges);
        detailBody.add(Box.createVerticalStrut(10));

        // title
        JLabel title = new JLabel("<html><body style='width:340px'><b>"
            + Theme.esc(d.name) + "</b></body></html>");
        title.setFont(Theme.f(Font.BOLD, 21));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailBody.add(title);

        if (!d.dept.trim().isEmpty()) {
            detailBody.add(Box.createVerticalStrut(4));
            detailBody.add(Theme.text(d.dept, 340, 13, Theme.MUTED));
        }

        // about
        if (!d.desc.trim().isEmpty()) {
            detailBody.add(sectionTitle("About this scheme"));
            detailBody.add(Theme.text(d.desc, 340, 13, Theme.TEXT));
        }

        // who can apply
        addBullets("Who can apply", items(d.elig, false));

        // benefits
        addBullets("What you get", items(d.benefits, false));

        // how to apply (timeline)
        List<String> steps = items(d.process, false);
        if (!steps.isEmpty()) {
            detailBody.add(sectionTitle("How to apply"));
            for (int i = 0; i < steps.size(); i++) {
                detailBody.add(new StepRow(i + 1, steps.get(i), i == steps.size() - 1));
            }
        }

        // documents checklist
        addChecklist(items(d.docs, true));

        // contact
        if (!d.contact.trim().isEmpty()) {
            detailBody.add(sectionTitle("Contact"));
            detailBody.add(Theme.text(d.contact, 340, 13, Theme.TEXT));
        }

        // buttons
        detailBody.add(Box.createVerticalStrut(18));

        if (!d.website.trim().isEmpty()) {
            Theme.Button web = new Theme.Button(
                "Open Official Website", Theme.GREEN, Color.WHITE);
            web.addActionListener(e -> Theme.openUrl(this, d.website));
            detailBody.add(web);
            detailBody.add(Box.createVerticalStrut(8));
        }

        if (canManage) {
            Theme.Button edit = new Theme.Button(
                "Edit this scheme", new Color(236, 242, 238), Theme.TEXT);
            edit.addActionListener(e -> openEditor(d.id));
            detailBody.add(edit);
        }

        detailBody.revalidate();
        detailBody.repaint();
    }

    private Theme.Badge statusBadge(String status) {
        return isActive(status)
            ? new Theme.Badge("Active", Theme.GREEN_SOFT, Theme.GREEN)
            : new Theme.Badge("Inactive", new Color(236, 236, 236), Theme.MUTED);
    }

    private void addBullets(String title, List<String> list) {
        if (list.isEmpty()) {
            return;
        }
        detailBody.add(sectionTitle(title));
        for (String s : list) {
            detailBody.add(Theme.text("\u2022  " + s, 340, 13, Theme.TEXT));
            detailBody.add(Box.createVerticalStrut(3));
        }
    }

    // Documents checklist with a "readiness" progress bar
    private void addChecklist(List<String> docs) {

        if (docs.isEmpty()) {
            return;
        }

        detailBody.add(sectionTitle("Documents checklist"));

        JLabel progressLabel = new JLabel();
        progressLabel.setFont(Theme.f(Font.PLAIN, 12));
        progressLabel.setForeground(Theme.MUTED);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Theme.ProgressBar bar = new Theme.ProgressBar();

        List<JCheckBox> boxes = new ArrayList<>();
        Runnable refresh = () -> {
            int done = 0;
            for (JCheckBox b : boxes) {
                if (b.isSelected()) {
                    done++;
                }
            }
            progressLabel.setText("Documents ready: " + done + " of " + docs.size()
                + (done == docs.size() ? "  - you are ready to apply!" : ""));
            bar.setValue((double) done / docs.size());
        };

        detailBody.add(progressLabel);
        detailBody.add(Box.createVerticalStrut(5));
        detailBody.add(bar);
        detailBody.add(Box.createVerticalStrut(6));

        for (String doc : docs) {
            JCheckBox box = new JCheckBox("<html><body style='width:310px'>"
                + Theme.esc(doc) + "</body></html>");
            box.setOpaque(false);
            box.setFont(Theme.f(Font.PLAIN, 13));
            box.setForeground(Theme.TEXT);
            box.setFocusPainted(false);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            box.addActionListener(e -> refresh.run());
            boxes.add(box);
            detailBody.add(box);
        }
        refresh.run();
    }

    // Splits saved text into separate items (new lines, ; and numbering)
    private static List<String> items(String text, boolean commaToo) {

        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }

        String t = text.replace("\r", "").trim();
        if (t.isEmpty()) {
            return out;
        }

        String[] parts = t.split(commaToo ? "\\n|;|," : "\\n|;");

        if (parts.length == 1 && !commaToo) {
            parts = t.split("(?<=\\.)\\s+");
        }

        for (String p : parts) {
            String s = p.trim().replaceFirst("^(\\d+[.)]|[-*\\u2022])\\s*", "");
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Editor dialog (Admin / NGO)
    // ------------------------------------------------------------------
    private void openEditor(int id) {

        if (!canManage) {
            JOptionPane.showMessageDialog(this, "Permission denied.");
            return;
        }

        Detail d = (id == -1) ? new Detail() : fetchDetail(id);
        if (d == null) {
            return;
        }

        JDialog dlg = new JDialog(this,
            id == -1 ? "New Government Scheme" : "Edit Government Scheme", true);
        dlg.setSize(680, 780);
        dlg.setLocationRelativeTo(this);

        JTextField name = Theme.field();
        JTextField type = Theme.field();
        JTextField dept = Theme.field();
        JTextField website = Theme.field();
        JTextField contact = Theme.field();
        JComboBox<String> status = new JComboBox<>(new String[]{"Active", "Inactive"});
        JComboBox<String> pillar = new JComboBox<>(CompassEngine.SCHEME_TAGS);
        if (id != -1) {
            String tag = CompassEngine.getSchemePillar(id);
            if (tag != null) {
                pillar.setSelectedItem(tag);
            }
        }

        JTextArea desc = Theme.area(3);
        JTextArea elig = Theme.area(3);
        JTextArea benefits = Theme.area(3);
        JTextArea docs = Theme.area(3);
        JTextArea process = Theme.area(4);

        name.setText(d.name);
        type.setText(d.type);
        dept.setText(d.dept);
        website.setText(d.website);
        contact.setText(d.contact);
        desc.setText(d.desc);
        elig.setText(d.elig);
        benefits.setText(d.benefits);
        docs.setText(d.docs);
        process.setText(d.process);
        status.setSelectedItem(isActive(d.status) ? "Active" : "Inactive");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 20));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;

        int r = 0;
        addRow(form, g, r++, "Scheme Name *", name);
        addRow(form, g, r++, "Scheme Type (e.g. Housing, Education)", type);
        addRow(form, g, r++, "Department", dept);
        addRow(form, g, r++, "Official Website", website);
        addRow(form, g, r++, "Contact Details", contact);
        addRow(form, g, r++, "Status", status);
        addRow(form, g, r++, "Strengthens pillar (used by the Scheme Matcher)", pillar);
        addRow(form, g, r++, "Description", Theme.scroll(desc));
        addRow(form, g, r++, "Eligibility (one point per line)", Theme.scroll(elig));
        addRow(form, g, r++, "Benefits (one point per line)", Theme.scroll(benefits));
        addRow(form, g, r++, "Required Documents (one per line)", Theme.scroll(docs));
        addRow(form, g, r++, "Application Process (one step per line)",
            Theme.scroll(process));

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(18);

        Theme.Button save = new Theme.Button("Save Scheme", Theme.GREEN, Color.WHITE);
        Theme.Button cancel = new Theme.Button(
            "Cancel", new Color(236, 242, 238), Theme.TEXT);
        cancel.addActionListener(e -> dlg.dispose());

        save.addActionListener(e -> {
            if (name.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Please enter the scheme name.");
                name.requestFocus();
                return;
            }

            Detail n = new Detail();
            n.id = d.id;
            n.name = name.getText().trim();
            n.type = type.getText().trim();
            n.dept = dept.getText().trim();
            n.website = website.getText().trim();
            n.contact = contact.getText().trim();
            n.desc = desc.getText().trim();
            n.elig = elig.getText().trim();
            n.benefits = benefits.getText().trim();
            n.docs = docs.getText().trim();
            n.process = process.getText().trim();
            n.status = status.getSelectedItem().toString().toUpperCase();

            if (saveScheme(n)) {
                String tag = pillar.getSelectedIndex() == 0
                    ? null : pillar.getSelectedItem().toString();
                CompassEngine.setSchemePillar(selectedId, tag);
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
        g.weighty = 0;
        p.add(l, g);

        g.gridy = row * 2 + 1;
        p.add(c, g);
    }

    // Insert or update (same SQL and columns as the old form)
    private boolean saveScheme(Detail d) {

        boolean adding = d.id == -1;

        String insert =
            "INSERT INTO GOVERNMENT_SCHEMES "
            + "(SCHEME_ID, SCHEME_NAME, SCHEME_TYPE, DEPARTMENT_NAME, "
            + "DESCRIPTION, ELIGIBILITY_CRITERIA, BENEFITS, "
            + "REQUIRED_DOCUMENTS, APPLICATION_PROCESS, OFFICIAL_WEBSITE, "
            + "CONTACT_DETAILS, STATUS, CREATED_DATE) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATE)";

        String update =
            "UPDATE GOVERNMENT_SCHEMES SET SCHEME_NAME=?, SCHEME_TYPE=?, "
            + "DEPARTMENT_NAME=?, DESCRIPTION=?, ELIGIBILITY_CRITERIA=?, "
            + "BENEFITS=?, REQUIRED_DOCUMENTS=?, APPLICATION_PROCESS=?, "
            + "OFFICIAL_WEBSITE=?, CONTACT_DETAILS=?, STATUS=? "
            + "WHERE SCHEME_ID=?";

        try (Connection con = DBConnection.getConnection()) {

            con.setAutoCommit(false);

            int savedId = d.id;

            if (adding) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT NVL(MAX(SCHEME_ID), 0) + 1 FROM GOVERNMENT_SCHEMES");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    savedId = rs.getInt(1);
                }

                try (PreparedStatement ps = con.prepareStatement(insert)) {
                    ps.setInt(1, savedId);
                    ps.setString(2, d.name);
                    ps.setString(3, d.type);
                    ps.setString(4, d.dept);
                    ps.setString(5, d.desc);
                    ps.setString(6, d.elig);
                    ps.setString(7, d.benefits);
                    ps.setString(8, d.docs);
                    ps.setString(9, d.process);
                    ps.setString(10, d.website);
                    ps.setString(11, d.contact);
                    ps.setString(12, d.status);
                    ps.executeUpdate();
                }

            } else {
                try (PreparedStatement ps = con.prepareStatement(update)) {
                    ps.setString(1, d.name);
                    ps.setString(2, d.type);
                    ps.setString(3, d.dept);
                    ps.setString(4, d.desc);
                    ps.setString(5, d.elig);
                    ps.setString(6, d.benefits);
                    ps.setString(7, d.docs);
                    ps.setString(8, d.process);
                    ps.setString(9, d.website);
                    ps.setString(10, d.contact);
                    ps.setString(11, d.status);
                    ps.setInt(12, d.id);

                    if (ps.executeUpdate() == 0) {
                        con.rollback();
                        JOptionPane.showMessageDialog(this, "Scheme not found.");
                        return false;
                    }
                }
            }

            con.commit();

            selectedId = savedId;
            loadSchemes();

            Detail fresh = fetchDetail(savedId);
            if (fresh != null) {
                showDetail(fresh);
            }

            JOptionPane.showMessageDialog(this, adding
                ? "Government scheme added successfully!"
                : "Government scheme updated successfully!");
            return true;

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to save scheme:\n" + ex.getMessage());
            return false;
        }
    }

    // ==================================================================
    // Custom components
    // ==================================================================

    // One scheme card in the gallery
    private class SchemeCard extends Theme.Card {

        private final Scheme scheme;
        private final Color accent;

        SchemeCard(Scheme s) {
            super(Color.WHITE);
            this.scheme = s;
            this.accent = Theme.accentFor(s.type);

            setLayout(new BorderLayout(0, 6));
            setBorder(BorderFactory.createEmptyBorder(14, 22, 16, 16));
            setPreferredSize(new Dimension(100, 165));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            String typeText = s.type.trim().isEmpty() ? "Scheme" : s.type;
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(new Theme.Badge(typeText, accent, Color.WHITE));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(statusBadge(s.status));

            top.add(left, BorderLayout.WEST);
            top.add(right, BorderLayout.EAST);

            JLabel name = new JLabel("<html><body style='width:230px'><b>"
                + Theme.esc(Theme.shorten(s.name, 60)) + "</b></body></html>");
            name.setFont(Theme.f(Font.BOLD, 15));
            name.setForeground(Theme.TEXT);

            JLabel dept = new JLabel("<html><body style='width:230px'>"
                + Theme.esc(Theme.shorten(s.dept, 50)) + "</body></html>");
            dept.setFont(Theme.f(Font.PLAIN, 12));
            dept.setForeground(Theme.MUTED);

            JPanel mid = new JPanel(new BorderLayout(0, 3));
            mid.setOpaque(false);
            mid.add(name, BorderLayout.NORTH);
            mid.add(dept, BorderLayout.CENTER);

            JLabel benefit = new JLabel("<html><body style='width:230px'>"
                + Theme.esc(Theme.shorten(s.benefits, 85)) + "</body></html>");
            benefit.setFont(Theme.f(Font.PLAIN, 12));
            benefit.setForeground(new Color(55, 75, 65));

            add(top, BorderLayout.NORTH);
            add(mid, BorderLayout.CENTER);
            add(benefit, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    select(scheme);
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

            // coloured stripe on the left edge
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Double(3, 1, 8, getHeight() - 7, 8, 8));
            g2.setColor(Color.WHITE);
            g2.fillRect(8, 1, 8, getHeight() - 7);
            g2.dispose();
        }
    }

    // One step of the "How to apply" timeline
    private static class StepRow extends JPanel {

        StepRow(int number, String text, boolean last) {

            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JComponent dot = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Theme.quality(g2);

                    int cx = 13;
                    if (!last) {
                        g2.setColor(new Color(190, 215, 200));
                        g2.fill(new Rectangle2D.Double(cx - 1, 26, 2, getHeight() - 24));
                    }
                    g2.setColor(Theme.GREEN);
                    g2.fill(new Ellipse2D.Double(0, 0, 26, 26));
                    g2.setColor(Color.WHITE);
                    g2.setFont(Theme.f(Font.BOLD, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    String s = String.valueOf(number);
                    g2.drawString(s, cx - fm.stringWidth(s) / 2,
                            (26 - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(26, 26));

            JLabel label = Theme.text(text, 300, 13, Theme.TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(3, 0, 12, 0));

            add(dot, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new GovernmentSchemesForm(1, "Demo User", "ADMIN").setVisible(true));
    }
}