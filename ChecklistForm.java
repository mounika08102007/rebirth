package rebirth;

import java.awt.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/**
 * Counsellor only: record the things the Compass cannot get from other
 * screens - documents held, place to stay, family contact, cases, parole
 * and the person's district.  Saving updates the Readiness Index at once.
 */
public class ChecklistForm extends JFrame {

    private final int userId;
    private final Runnable onSaved;

    private final JComboBox<CounsellorUtil.Item> personBox =
        Theme.flat(new JComboBox<CounsellorUtil.Item>());
    private final Map<String, JComboBox<String>> docBoxes = new LinkedHashMap<>();
    private final JComboBox<String> housing = CounsellorUtil.combo(CompassEngine.HOUSING);
    private final JComboBox<String> family = CounsellorUtil.combo(CompassEngine.FAMILY);
    private final JComboBox<String> cases = CounsellorUtil.combo(CompassEngine.CASES);
    private final JComboBox<String> parole = CounsellorUtil.combo(CompassEngine.PAROLE);
    private final JComboBox<String> district = CounsellorUtil.combo();
    private final JLabel preview = new JLabel(" ");
    private boolean loading;

    public ChecklistForm(int userId, String fullName, String role,
            int prisonerId, Runnable onSaved) {

        this.userId = userId;
        this.onSaved = onSaved;

        setTitle("REbirth - Reintegration Checklist");
        setSize(900, 820);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        district.addItem("");
        for (String d : CompassEngine.DISTRICTS) {
            district.addItem(d);
        }
        for (String t : CompassEngine.DOC_TYPES) {
            docBoxes.put(t, CounsellorUtil.combo(CompassEngine.DOC_STATES));
        }

        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.ensure(con);
            for (CounsellorUtil.Item i : CounsellorUtil.prisoners(con)) {
                personBox.addItem(i);
                if (i.id == prisonerId) {
                    personBox.setSelectedItem(i);
                }
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("Reintegration Checklist",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel card = CounsellorUtil.formCard();
        int r = 0;
        personBox.setFont(Theme.f(Font.PLAIN, 14));
        personBox.setPreferredSize(new Dimension(320, 36));
        CounsellorUtil.addRow(card, r++, "Person", personBox);
        CounsellorUtil.addRow(card, r++, "District", district);
        for (Map.Entry<String, JComboBox<String>> e : docBoxes.entrySet()) {
            CounsellorUtil.addRow(card, r++, "Document: " + e.getKey(), e.getValue());
        }
        CounsellorUtil.addRow(card, r++, "Place to stay", housing);
        CounsellorUtil.addRow(card, r++, "Family contact", family);
        CounsellorUtil.addRow(card, r++, "Cases", cases);
        CounsellorUtil.addRow(card, r++, "Parole conditions", parole);

        preview.setFont(Theme.f(Font.BOLD, 14));
        preview.setForeground(Theme.GREEN_DARK);
        CounsellorUtil.addRow(card, r++, "", preview);

        Theme.Button save = new Theme.Button("Save Checklist", Theme.GREEN, Color.WHITE);
        save.addActionListener(e -> save());
        CounsellorUtil.addRow(card, r, "", CounsellorUtil.buttonRow(save));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        body.add(card, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null);
        Theme.slim(sp);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        root.add(sp, BorderLayout.CENTER);
        setContentPane(root);

        personBox.addActionListener(e -> load());
        load();
    }

    private int pid() {
        CounsellorUtil.Item it = (CounsellorUtil.Item) personBox.getSelectedItem();
        return it == null ? 0 : it.id;
    }

    private void load() {

        int pid = pid();
        if (pid == 0) {
            return;
        }
        loading = true;
        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.Compass c = CompassEngine.compute(con, pid);
            for (Map.Entry<String, JComboBox<String>> e : docBoxes.entrySet()) {
                String st = c.docs.get(e.getKey());
                e.getValue().setSelectedItem(st == null ? "Missing" : st);
            }
            housing.setSelectedItem(c.housing);
            family.setSelectedItem(c.family);
            cases.setSelectedItem(c.caseStatus);
            parole.setSelectedItem(c.parole);
            district.setSelectedItem(c.district);
            preview.setText("Current readiness index: " + Math.round(c.index) + " / 100");
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        } finally {
            loading = false;
        }
    }

    private void save() {

        int pid = pid();
        if (pid == 0 || loading) {
            CounsellorUtil.error(this, "Select a person.");
            return;
        }
        Map<String, String> docs = new LinkedHashMap<>();
        for (Map.Entry<String, JComboBox<String>> e : docBoxes.entrySet()) {
            docs.put(e.getKey(), e.getValue().getSelectedItem().toString());
        }
        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.saveChecklist(con, pid, docs,
                housing.getSelectedItem().toString(),
                family.getSelectedItem().toString(),
                cases.getSelectedItem().toString(),
                parole.getSelectedItem().toString(),
                district.getSelectedItem().toString());
            CompassEngine.Compass c = CompassEngine.compute(con, pid);
            CompassEngine.snapshot(con, c);
            preview.setText("Saved. New readiness index: " + Math.round(c.index) + " / 100");
            CounsellorUtil.info(this, "Checklist saved.\nReadiness index is now "
                + Math.round(c.index) + " / 100.");
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }
}