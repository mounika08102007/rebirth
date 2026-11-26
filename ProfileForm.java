package rebirth;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * REbirth - My Profile (works for every role).
 *
 *   Everyone       : full name, username (locked), Gmail, phone, change password
 *   Prisoner       : + age, gender, address, education, skills, release status,
 *                      employment status, counselling required
 *                      (arrest reason and release date are shown but locked)
 *   Counsellor     : + qualification, specialization, experience, availability,
 *                      counselling mode, status
 *   NGO / Admin    : account details only (same as registration)
 *
 * Uses Theme.java for the look (HeaderBand, Card, Badge, Button, field()).
 *
 * Open it from the dashboard:
 *   new ProfileForm(userId, fullName, role, newName -> { ...refresh... }).setVisible(true);
 */
public class ProfileForm extends JFrame {

    // Same rules as RegistrationForm
    private static final String PASSWORD_REGEX = "^(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$";
    private static final String EMAIL_REGEX    = "^[A-Za-z0-9._%+-]+@gmail\\.com$";
    private static final String PHONE_REGEX    = "\\d{10}";

    private static final Color LOCKED_BG = new Color(238, 242, 239);

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private final int userId;
    private String fullName;
    private final String role;
    private final boolean isPrisoner;
    private final boolean isCounsellor;
    private final Consumer<String> onNameChanged;

    private Map<String, String> loaded = new HashMap<>();
    private boolean editing = false;

    // ------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------
    // common
    private final JTextField nameF = Theme.field();
    private final JTextField usernameF = Theme.field();
    private final JTextField emailF = Theme.field();
    private final JTextField phoneF = Theme.field();

    // prisoner
    private final JTextField ageF = Theme.field();
    private final JTextField addressF = Theme.field();
    private final JTextField educationF = Theme.field();
    private final JTextField skillsF = Theme.field();
    private final JTextField arrestF = Theme.field();        // locked
    private final JTextField releaseDateF = Theme.field();   // locked
    private final JComboBox<String> genderB = combo("Male", "Female", "Other");
    private final JComboBox<String> releaseStatusB = combo("Released", "On Parole");
    private final JComboBox<String> employmentB = combo("Employed", "Unemployed", "Student");
    private final JComboBox<String> counsellingB = combo("Yes", "No");

    // counsellor
    private final JTextField qualificationF = Theme.field();
    private final JTextField specializationF = Theme.field();
    private final JTextField experienceF = Theme.field();
    private final JTextField availabilityF = Theme.field();
    private final JComboBox<String> modeB = combo("Online", "In-Person", "Both");
    private final JComboBox<String> counsellorStatusB = combo("Active", "Inactive");

    // fields that become editable when "Edit Profile" is pressed
    private final List<JComponent> editables = new ArrayList<>();

    private Avatar avatar;
    private JLabel nameLabel;
    private JLabel idLabel;
    private JLabel message;
    private Theme.Button editBtn, saveBtn, cancelBtn;
    private JPanel saveBar;

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------
    public ProfileForm(int userId, String fullName, String role) {
        this(userId, fullName, role, null);
    }

    /** onNameChanged is called (with the new name) after a successful save if the name changed. */
    public ProfileForm(int userId, String fullName, String role,
                       Consumer<String> onNameChanged) {

        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.isPrisoner = role.equalsIgnoreCase("Prisoner");
        this.isCounsellor = role.equalsIgnoreCase("Counsellor");
        this.onNameChanged = onNameChanged;

        setTitle("REbirth - My Profile");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(900, 620));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // locked fields never become editable
        for (JTextField t : new JTextField[]{usernameF, arrestF, releaseDateF}) {
            lock(t);
        }

        JPanel root = new Theme.Backdrop(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        setContentPane(root);

        setEditing(false);
        loadProfile();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------
    private JComponent buildHeader() {
        Theme.HeaderBand band = new Theme.HeaderBand("My Profile",
            "View and update your registered details");
        Theme.Button close = new Theme.Button("Close", Color.WHITE, Theme.GREEN_DARK);
        close.addActionListener(e -> dispose());
        band.addRight(close);
        return band;
    }

    private JComponent buildBody() {

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTH;
        g.gridy = 0;

        // left: identity card
        g.gridx = 0;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.insets = new Insets(0, 0, 0, 20);
        content.add(buildIdentityCard(), g);

        // right: detail cards + save bar
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 0, 0);
        content.add(buildRightColumn(), g);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(content, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    private JComponent buildIdentityCard() {

        Theme.Card card = new Theme.Card(Color.WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(24, 20, 30, 20));
        card.setPreferredSize(new Dimension(280, 400));

        avatar = new Avatar(initialOf(fullName));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        nameLabel = new JLabel(fullName);
        nameLabel.setFont(Theme.f(Font.BOLD, 20));
        nameLabel.setForeground(Theme.TEXT);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Theme.Badge badge = new Theme.Badge(prettyRole(role).toUpperCase(),
            Theme.GREEN_SOFT, Theme.GREEN_DARK);
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);

        idLabel = new JLabel(" ");
        idLabel.setFont(Theme.f(Font.PLAIN, 13));
        idLabel.setForeground(Theme.MUTED);
        idLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        editBtn = new Theme.Button("Edit Profile", Theme.GREEN, Color.WHITE);
        editBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        editBtn.addActionListener(e -> setEditing(true));

        Theme.Button pwdBtn = new Theme.Button("Change Password",
            Theme.GREEN_SOFT, Theme.GREEN_DARK);
        pwdBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        pwdBtn.addActionListener(e -> changePassword());

        card.add(avatar);
        card.add(Box.createVerticalStrut(14));
        card.add(nameLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(badge);
        card.add(Box.createVerticalStrut(8));
        card.add(idLabel);
        card.add(Box.createVerticalGlue());
        card.add(editBtn);
        card.add(Box.createVerticalStrut(10));
        card.add(pwdBtn);
        return card;
    }

    private JComponent buildRightColumn() {

        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH;
        g.insets = new Insets(0, 0, 14, 0);

        int row = 0;

        // ---- account (everyone) ----
        g.gridy = row++;
        col.add(card("Account Details", new Object[][]{
            {"Full Name", nameF, "Username (cannot be changed)", usernameF},
            {"Gmail Address", emailF, "Phone Number", phoneF}
        }), g);
        editables.add(nameF);
        editables.add(emailF);
        editables.add(phoneF);

        // ---- role specific ----
        if (isPrisoner) {
            g.gridy = row++;
            col.add(card("Personal Details", new Object[][]{
                {"Age", ageF, "Gender", genderB},
                {"Address", addressF, null, null},
                {"Education Qualification", educationF, "Skills", skillsF}
            }), g);

            g.gridy = row++;
            col.add(card("Rehabilitation Details", new Object[][]{
                {"Reason for Arrest (locked)", arrestF, "Release Date (locked)", releaseDateF},
                {"Release Status", releaseStatusB, "Employment Status", employmentB},
                {"Counselling Required", counsellingB, null, null}
            }), g);

            editables.add(ageF);
            editables.add(genderB);
            editables.add(addressF);
            editables.add(educationF);
            editables.add(skillsF);
            editables.add(releaseStatusB);
            editables.add(employmentB);
            editables.add(counsellingB);

        } else if (isCounsellor) {
            g.gridy = row++;
            col.add(card("Professional Details", new Object[][]{
                {"Qualification", qualificationF, "Specialization", specializationF},
                {"Years of Experience", experienceF, "Availability", availabilityF},
                {"Counselling Mode", modeB, "Status", counsellorStatusB}
            }), g);

            editables.add(qualificationF);
            editables.add(specializationF);
            editables.add(experienceF);
            editables.add(availabilityF);
            editables.add(modeB);
            editables.add(counsellorStatusB);
        }

        // ---- message + Save / Cancel ----
        message = new JLabel(" ");
        message.setFont(Theme.f(Font.BOLD, 13));

        saveBtn = new Theme.Button("Save Changes", Theme.GREEN, Color.WHITE);
        cancelBtn = new Theme.Button("Cancel", Theme.GREEN_SOFT, Theme.GREEN_DARK);
        saveBtn.addActionListener(e -> save());
        cancelBtn.addActionListener(e -> {
            fill(loaded);
            setEditing(false);
            say(" ", false);
        });

        saveBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        saveBar.setOpaque(false);
        saveBar.add(cancelBtn);
        saveBar.add(saveBtn);

        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.add(message, BorderLayout.CENTER);
        bar.add(saveBar, BorderLayout.EAST);

        g.gridy = row++;
        g.insets = new Insets(0, 4, 0, 4);
        col.add(bar, g);

        return col;
    }

    /** White card with a title and a two-column grid of labelled fields. */
    private Theme.Card card(String title, Object[][] rows) {

        Theme.Card card = new Theme.Card(Color.WHITE);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 24, 20));

        JLabel t = new JLabel(title);
        t.setFont(Theme.f(Font.BOLD, 16));
        t.setForeground(Theme.GREEN_DARK);
        card.add(t, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        for (int r = 0; r < rows.length; r++) {
            Object[] row = rows[r];
            boolean single = row[2] == null;

            GridBagConstraints g = new GridBagConstraints();
            g.gridy = r;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.NORTH;

            g.gridx = 0;
            g.gridwidth = single ? 2 : 1;
            g.insets = new Insets(5, 0, 5, single ? 0 : 8);
            grid.add(labelled((String) row[0], (JComponent) row[1]), g);

            if (!single) {
                g.gridx = 1;
                g.gridwidth = 1;
                g.insets = new Insets(5, 8, 5, 0);
                grid.add(labelled((String) row[2], (JComponent) row[3]), g);
            }
        }
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel labelled(String text, JComponent c) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.f(Font.BOLD, 12));
        l.setForeground(Theme.MUTED);

        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(l, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static JComboBox<String> combo(String... items) {
        JComboBox<String> b = new JComboBox<>(items);
        b.setFont(Theme.f(Font.PLAIN, 14));
        b.setBackground(Color.WHITE);
        b.setPreferredSize(new Dimension(200, 36));
        return b;
    }

    private static void lock(JTextField t) {
        t.setEditable(false);
        t.setBackground(LOCKED_BG);
    }

    // ------------------------------------------------------------------
    // View mode / edit mode
    // ------------------------------------------------------------------
    private void setEditing(boolean on) {
        editing = on;

        for (JComponent c : editables) {
            if (c instanceof JTextField) {
                JTextField t = (JTextField) c;
                t.setEditable(on);
                t.setBackground(on ? Color.WHITE : LOCKED_BG);
            } else {
                c.setEnabled(on);
            }
        }
        editBtn.setVisible(!on);
        saveBar.setVisible(on);
    }

    private void say(String text, boolean error) {
        message.setForeground(error ? Theme.RED : Theme.GREEN);
        message.setText(text);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------
    private void loadProfile() {
        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() throws Exception {
                return fetch();
            }

            @Override
            protected void done() {
                try {
                    loaded = get();
                    fill(loaded);
                } catch (Exception ex) {
                    say("Could not load profile: " + rootMessage(ex), true);
                }
            }
        }.execute();
    }

    private Map<String, String> fetch() throws SQLException {

        Map<String, String> m = new HashMap<>();

        try (Connection con = DBConnection.getConnection()) {

            readRow(con, m,
                "SELECT FULL_NAME, USERNAME, EMAIL, PHONE FROM USERS WHERE USER_ID = ?",
                "name", "username", "email", "phone");

            if (isPrisoner) {
                readRow(con, m,
                    "SELECT PRISONER_ID, AGE, GENDER, ADDRESS, EDUCATION, SKILLS, "
                    + "ARREST_REASON, TO_CHAR(RELEASE_DATE, 'YYYY-MM-DD'), "
                    + "RELEASE_STATUS, EMPLOYMENT_STATUS, COUNSELLING_REQUIRED "
                    + "FROM PRISONER_DETAILS WHERE USER_ID = ?",
                    "id", "age", "gender", "address", "education", "skills",
                    "arrest", "releaseDate", "releaseStatus", "employment",
                    "counselling");

            } else if (isCounsellor) {
                readRow(con, m,
                    "SELECT COUNSELLOR_ID, QUALIFICATION, SPECIALIZATION, "
                    + "EXPERIENCE_YEARS, AVAILABILITY, COUNSELLING_MODE, STATUS "
                    + "FROM COUNSELLOR_DETAILS WHERE USER_ID = ?",
                    "id", "qualification", "specialization", "experience",
                    "availability", "mode", "counsellorStatus");
            }
        }
        return m;
    }

    private void readRow(Connection con, Map<String, String> m, String sql,
                         String... keys) throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    for (int i = 0; i < keys.length; i++) {
                        m.put(keys[i], Theme.nz(rs.getString(i + 1)));
                    }
                }
            }
        }
    }

    /** Puts a loaded / saved map on the screen. */
    private void fill(Map<String, String> m) {

        nameF.setText(get(m, "name"));
        usernameF.setText(get(m, "username"));
        emailF.setText(get(m, "email"));
        phoneF.setText(get(m, "phone"));

        if (isPrisoner) {
            ageF.setText(get(m, "age"));
            addressF.setText(get(m, "address"));
            educationF.setText(get(m, "education"));
            skillsF.setText(get(m, "skills"));
            arrestF.setText(get(m, "arrest"));
            releaseDateF.setText(get(m, "releaseDate"));
            pick(genderB, get(m, "gender"));
            pick(releaseStatusB, get(m, "releaseStatus"));
            pick(employmentB, get(m, "employment"));
            pick(counsellingB, get(m, "counselling"));
            idLabel.setText("Prisoner ID: " + get(m, "id"));

        } else if (isCounsellor) {
            qualificationF.setText(get(m, "qualification"));
            specializationF.setText(get(m, "specialization"));
            experienceF.setText(get(m, "experience"));
            availabilityF.setText(get(m, "availability"));
            pick(modeB, get(m, "mode"));
            pick(counsellorStatusB, get(m, "counsellorStatus"));
            idLabel.setText("Counsellor ID: " + get(m, "id"));

        } else {
            idLabel.setText("User ID: " + userId);
        }
    }

    private static String get(Map<String, String> m, String key) {
        return Theme.nz(m.get(key));
    }

    private static void pick(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).equalsIgnoreCase(value.trim())) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------
    /** Current values on screen. */
    private Map<String, String> snapshot() {

        Map<String, String> m = new HashMap<>(loaded);   // keeps id, locked values, etc.

        m.put("name", nameF.getText().trim());
        m.put("email", emailF.getText().trim());
        m.put("phone", phoneF.getText().trim());

        if (isPrisoner) {
            m.put("age", ageF.getText().trim());
            m.put("gender", String.valueOf(genderB.getSelectedItem()));
            m.put("address", addressF.getText().trim());
            m.put("education", educationF.getText().trim());
            m.put("skills", skillsF.getText().trim());
            m.put("releaseStatus", String.valueOf(releaseStatusB.getSelectedItem()));
            m.put("employment", String.valueOf(employmentB.getSelectedItem()));
            m.put("counselling", String.valueOf(counsellingB.getSelectedItem()));

        } else if (isCounsellor) {
            m.put("qualification", qualificationF.getText().trim());
            m.put("specialization", specializationF.getText().trim());
            m.put("experience", experienceF.getText().trim());
            m.put("availability", availabilityF.getText().trim());
            m.put("mode", String.valueOf(modeB.getSelectedItem()));
            m.put("counsellorStatus", String.valueOf(counsellorStatusB.getSelectedItem()));
        }
        return m;
    }

    /** Returns an error message, or null if everything is valid. */
    private String validate(Map<String, String> m) {

        if (get(m, "name").isEmpty()) {
            return "Full name cannot be empty.";
        }
        if (!get(m, "email").matches(EMAIL_REGEX)) {
            return "Enter a valid Gmail address.";
        }
        if (!get(m, "phone").matches(PHONE_REGEX)) {
            return "Phone number must contain exactly 10 digits.";
        }

        if (isPrisoner) {
            int age;
            try {
                age = Integer.parseInt(get(m, "age"));
            } catch (NumberFormatException ex) {
                return "Age must be a valid number.";
            }
            if (age < 1 || age > 120) {
                return "Age must be between 1 and 120.";
            }
            if (get(m, "address").isEmpty() || get(m, "education").isEmpty()
                    || get(m, "skills").isEmpty()) {
                return "Address, education and skills cannot be empty.";
            }

        } else if (isCounsellor) {
            int years;
            try {
                years = Integer.parseInt(get(m, "experience"));
            } catch (NumberFormatException ex) {
                return "Experience must be a valid number.";
            }
            if (years < 0 || years > 99) {
                return "Experience must be between 0 and 99 years.";
            }
            if (get(m, "qualification").isEmpty() || get(m, "specialization").isEmpty()
                    || get(m, "availability").isEmpty()) {
                return "Qualification, specialization and availability cannot be empty.";
            }
        }
        return null;
    }

    private void save() {

        final Map<String, String> now = snapshot();
        String error = validate(now);
        if (error != null) {
            say(error, true);
            return;
        }

        setBusy(true);
        say("Saving...", false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                writeToDatabase(now);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                } catch (Exception ex) {
                    String msg = rootMessage(ex);
                    say(msg.toUpperCase().contains("ORA-00001")
                        ? "That email or phone is already used by another account."
                        : "Could not save: " + msg, true);
                    return;
                }

                loaded = now;
                setEditing(false);
                say("Profile updated successfully.", false);

                String newName = ProfileForm.get(now, "name");
                if (!newName.equals(fullName)) {
                    fullName = newName;
                    nameLabel.setText(newName);
                    avatar.setInitial(initialOf(newName));
                    if (onNameChanged != null) {
                        onNameChanged.accept(newName);
                    }
                }
            }
        }.execute();
    }

    /** USERS plus the role table, in ONE transaction. */
    private void writeToDatabase(Map<String, String> m) throws SQLException {

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE USERS SET FULL_NAME = ?, EMAIL = ?, PHONE = ? "
                        + "WHERE USER_ID = ?")) {
                    ps.setString(1, get(m, "name"));
                    ps.setString(2, get(m, "email"));
                    ps.setString(3, get(m, "phone"));
                    ps.setInt(4, userId);
                    ps.executeUpdate();
                }

                if (isPrisoner) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE PRISONER_DETAILS SET AGE = ?, GENDER = ?, "
                            + "ADDRESS = ?, EDUCATION = ?, SKILLS = ?, "
                            + "RELEASE_STATUS = ?, EMPLOYMENT_STATUS = ?, "
                            + "COUNSELLING_REQUIRED = ? WHERE USER_ID = ?")) {
                        ps.setInt(1, Integer.parseInt(get(m, "age")));
                        ps.setString(2, get(m, "gender"));
                        ps.setString(3, get(m, "address"));
                        ps.setString(4, get(m, "education"));
                        ps.setString(5, get(m, "skills"));
                        ps.setString(6, get(m, "releaseStatus"));
                        ps.setString(7, get(m, "employment"));
                        ps.setString(8, get(m, "counselling"));
                        ps.setInt(9, userId);
                        ps.executeUpdate();
                    }

                } else if (isCounsellor) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE COUNSELLOR_DETAILS SET QUALIFICATION = ?, "
                            + "SPECIALIZATION = ?, EXPERIENCE_YEARS = ?, PHONE = ?, "
                            + "AVAILABILITY = ?, COUNSELLING_MODE = ?, STATUS = ? "
                            + "WHERE USER_ID = ?")) {
                        ps.setString(1, get(m, "qualification"));
                        ps.setString(2, get(m, "specialization"));
                        ps.setInt(3, Integer.parseInt(get(m, "experience")));
                        ps.setString(4, get(m, "phone"));
                        ps.setString(5, get(m, "availability"));
                        ps.setString(6, get(m, "mode"));
                        ps.setString(7, get(m, "counsellorStatus"));
                        ps.setInt(8, userId);
                        ps.executeUpdate();
                    }
                }

                con.commit();

            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private void setBusy(boolean busy) {
        saveBtn.setEnabled(!busy);
        cancelBtn.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
                       : Cursor.getDefaultCursor());
    }

    // ------------------------------------------------------------------
    // Change password
    // ------------------------------------------------------------------
    private void changePassword() {

        JPasswordField current = new JPasswordField(20);
        JPasswordField fresh = new JPasswordField(20);
        JPasswordField again = new JPasswordField(20);

        Object[] form = {
            "Current password:", current,
            "New password (8+ characters, a number and a symbol):", fresh,
            "Confirm new password:", again
        };

        final String oldPwd;
        final String newPwd;

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Change Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }

            String o = new String(current.getPassword());
            String n = new String(fresh.getPassword());
            String c = new String(again.getPassword());

            String problem = null;
            if (o.isEmpty() || n.isEmpty() || c.isEmpty()) {
                problem = "Please fill in all three boxes.";
            } else if (!n.matches(PASSWORD_REGEX)) {
                problem = "New password must have at least 8 characters, "
                        + "one number and one special character.";
            } else if (!n.equals(c)) {
                problem = "New password and confirmation do not match.";
            } else if (n.equals(o)) {
                problem = "New password must be different from the current one.";
            }

            if (problem == null) {
                oldPwd = o;
                newPwd = n;
                break;
            }
            JOptionPane.showMessageDialog(this, problem, "Change Password",
                JOptionPane.WARNING_MESSAGE);
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {

                    String stored = null;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT PASSWORD FROM USERS WHERE USER_ID = ?")) {
                        ps.setInt(1, userId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                stored = rs.getString(1);
                            }
                        }
                    }

                    if (stored == null || !PasswordUtil.verifyPassword(oldPwd, stored)) {
                        return "Current password is incorrect.";
                    }

                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE USERS SET PASSWORD = ? WHERE USER_ID = ?")) {
                        ps.setString(1, PasswordUtil.hashPassword(newPwd));
                        ps.setInt(2, userId);
                        ps.executeUpdate();
                    }
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String problem = get();
                    if (problem == null) {
                        say("Password changed successfully.", false);
                    } else {
                        JOptionPane.showMessageDialog(ProfileForm.this, problem,
                            "Change Password", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ProfileForm.this,
                        "Could not change password: " + rootMessage(ex),
                        "Change Password", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    private static String rootMessage(Exception ex) {
        Throwable t = (ex instanceof ExecutionException && ex.getCause() != null)
            ? ex.getCause() : ex;
        return Theme.nz(t.getMessage());
    }

    private static String initialOf(String name) {
        String n = Theme.nz(name).trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
    }

    private static String prettyRole(String r) {
        if (r.equalsIgnoreCase("NGO")) {
            return "NGO";
        }
        if (r.equalsIgnoreCase("Admin") || r.equalsIgnoreCase("Administrator")) {
            return "Administrator";
        }
        String t = r.trim();
        return t.isEmpty() ? t : t.substring(0, 1).toUpperCase()
                               + t.substring(1).toLowerCase();
    }

    // ------------------------------------------------------------------
    // Round avatar with the user's initial
    // ------------------------------------------------------------------
    private static class Avatar extends JComponent {

        private String initial;

        Avatar(String initial) {
            this.initial = initial;
            setPreferredSize(new Dimension(96, 96));
            setMaximumSize(new Dimension(96, 96));
        }

        void setInitial(String s) {
            initial = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            g2.setPaint(new GradientPaint(0, 0, new Color(52, 150, 105),
                96, 96, Theme.GREEN_DARK));
            g2.fill(new Ellipse2D.Double(0, 0, 96, 96));
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.f(Font.BOLD, 40));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, (96 - fm.stringWidth(initial)) / 2,
                (96 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Quick test (normally opened from the dashboard)
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new ProfileForm(1, "Arun Kumar", "Prisoner").setVisible(true));
    }
}
