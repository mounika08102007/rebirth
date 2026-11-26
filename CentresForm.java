package rebirth;

import java.awt.*;
import java.sql.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Administrator: keep the list of rehabilitation centres, NGO partners and
 * training partners. The Tamil Nadu map counts these rows, nothing is made up.
 */
public class CentresForm extends JFrame {

    private final JTextField name = Theme.field();
    private final JComboBox<String> type = CounsellorUtil.combo(CompassEngine.CENTRE_TYPES);
    private final JComboBox<String> district = CounsellorUtil.combo(CompassEngine.DISTRICTS);
    private final DefaultTableModel model = CounsellorUtil.readOnlyModel(
        "ID", "Name", "Type", "District", "");
    private final JTable table = CounsellorUtil.table(model);
    private final Runnable onChange;

    public CentresForm(String fullName, String role, Runnable onChange) {

        this.onChange = onChange;
        setTitle("REbirth - Centres and Partners");
        setSize(1000, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(330);
        table.getColumnModel().getColumn(4).setMaxWidth(70);

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(new Theme.HeaderBand("Centres and Partners",
            "Welcome, " + fullName + "  |  Role: " + role), BorderLayout.NORTH);

        JPanel card = CounsellorUtil.formCard();
        CounsellorUtil.addRow(card, 0, "Name", name);
        CounsellorUtil.addRow(card, 1, "Type", type);
        CounsellorUtil.addRow(card, 2, "District", district);

        Theme.Button add = new Theme.Button("Add", Theme.GREEN, Color.WHITE);
        Theme.Button del = new Theme.Button("Delete Selected", Theme.RED, Color.WHITE);
        add.addActionListener(e -> add());
        del.addActionListener(e -> delete());
        CounsellorUtil.addRow(card, 3, "", CounsellorUtil.buttonRow(add, del));

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        body.add(card, BorderLayout.NORTH);
        body.add(CounsellorUtil.scroll(table), BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        load();
    }

    private void load() {
        model.setRowCount(0);
        try (Connection con = DBConnection.getConnection()) {
            List<Object[]> rows = CompassEngine.listCentres(con);
            for (Object[] r : rows) {
                model.addRow(r);
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void add() {
        String n = name.getText().trim();
        if (n.isEmpty() || n.length() > 120) {
            CounsellorUtil.error(this, "Enter a name (max 120 characters).");
            return;
        }
        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.addCentre(con, n, type.getSelectedItem().toString(),
                district.getSelectedItem().toString());
            name.setText("");
            load();
            changed();
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void delete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            CounsellorUtil.error(this, "Select a row first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Delete this entry?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection con = DBConnection.getConnection()) {
            CompassEngine.deleteCentre(con, (Integer) model.getValueAt(row, 0));
            load();
            changed();
        } catch (SQLException ex) {
            CounsellorUtil.error(this, ex.getMessage());
        }
    }

    private void changed() {
        if (onChange != null) {
            onChange.run();
        }
    }
}