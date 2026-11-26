package rebirth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * REbirth - Create Account
 *
 * A 3-step registration wizard:
 *   Step 1  Account   : name, username, password, Gmail, phone
 *   Step 2  Role      : security question + answer, choose your role
 *   Step 3  Details   : extra details for Prisoner / Counsellor
 *
 * What is new in the look and feel
 *   - Split screen: a "growing sapling" on the left that grows while you fill the form
 *   - Step indicator, live green tick / red mark on every field
 *   - Password strength meter with a checklist
 *   - Picture role cards, smooth hover effects, shake animation on errors
 *
 * The DATABASE part (SQL, tables, columns, password hashing) is the same as before.
 */
public class RegistrationForm extends JFrame {

    // =====================================================================
    //  1. COLOURS, FONTS AND RULES  (change them here to re-theme the page)
    // =====================================================================
    private static final Color DARK_GREEN  = new Color(13, 75, 49);
    private static final Color LIGHT_GREEN = new Color(24, 120, 76);
    private static final Color CREAM       = new Color(250, 248, 238);
    private static final Color SOFT_WHITE  = new Color(255, 255, 250);
    private static final Color FIELD_BG    = new Color(255, 255, 253);
    private static final Color BORDER      = new Color(190, 196, 184);
    private static final Color HINT        = new Color(150, 156, 150);
    private static final Color TEXT        = new Color(38, 52, 46);
    private static final Color ERROR_RED   = new Color(178, 34, 34);
    private static final Color OK_GREEN    = new Color(34, 139, 84);

    private static final int SHADOW = 16;   // space around the white card for its shadow

    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font INPUT_FONT = new Font("Segoe UI", Font.PLAIN, 15);

    // Validation rules (same rules as the old form)
    private static final String PASSWORD_REGEX = "^(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$";
    private static final String EMAIL_REGEX    = "^[A-Za-z0-9._%+-]+@gmail\\.com$";
    private static final String PHONE_REGEX    = "\\d{10}";

    // Wizard steps
    private static final int STEP_ACCOUNT = 0;
    private static final int STEP_ROLE    = 1;
    private static final int STEP_DETAILS = 2;
    private static final String[] STEP_NAMES = {"Account", "Role & Security", "Your Details"};

    // =====================================================================
    //  2. COMPONENTS
    // =====================================================================

    // Step 1
    private InputField nameField, usernameField, emailField, phoneField;
    private PassField passwordField;
    private PasswordHelper passwordHelper;

    // Step 2
    private SoftCombo securityQuestionBox;
    private InputField securityAnswerField;
    private RoleCard[] roleCards;
    private String selectedRole = null;          // "PRISONER", "COUNSELLOR" or "NGO"

    // Step 3 - prisoner
    private InputField ageField, addressField, educationField;
    private InputField skillsField, arrestReasonField, releaseDateField;
    private SoftCombo genderBox, releaseStatusBox, employmentBox;
    private JRadioButton counsellingYes, counsellingNo;
    private ButtonGroup counsellingGroup;

    // Step 3 - counsellor
    private InputField qualificationField, specializationField;
    private InputField experienceField, availabilityField;
    private SoftCombo modeBox, counsellorStatusBox;

    // Layout pieces
    private HeroPanel hero;
    private Stepper stepper;
    private CardLayout pageLayout;
    private JPanel pages;
    private JPanel detailsPanel;
    private JLabel messageLabel;
    private RoundedButton backButton, nextButton, clearButton;
    private JPanel rightPanel;

    private int step = STEP_ACCOUNT;
    private boolean busy = false;
    private boolean shaking = false;

    // =====================================================================
    //  3. CONSTRUCTOR
    // =====================================================================
    public RegistrationForm() {

        setTitle("REbirth - Create Account");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));

        createFields();            // build every input box once
        createListeners();         // progress + message clearing

        BackgroundPanel background = new BackgroundPanel();
        background.add(createShell());
        setContentPane(background);

        goTo(STEP_ACCOUNT);
        refreshProgress();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // =====================================================================
    //  4. BUILDING THE SCREEN
    // =====================================================================

    /** Creates all the input components (nothing is placed on screen here). */
    private void createFields() {

        // ---- Step 1 ----
        nameField = new InputField("e.g. Ravi Kumar", RegistrationForm::notEmpty);
        usernameField = new InputField("Choose a unique username", RegistrationForm::notEmpty);
        passwordField = new PassField("8+ characters, a number and a symbol",
                s -> s.matches(PASSWORD_REGEX));
        emailField = new InputField("yourname@gmail.com", s -> s.matches(EMAIL_REGEX));
        phoneField = new InputField("10-digit mobile number", s -> s.matches(PHONE_REGEX));
        limitToDigits(phoneField, 10);

        passwordHelper = new PasswordHelper();

        // ---- Step 2 ----
        securityQuestionBox = new SoftCombo(new String[]{
            "Select Security Question",
            "What is your favorite color?",
            "Who was your childhood best friend?",
            "What was your first school?",
            "What is your favorite food?"
        });
        securityAnswerField = new InputField("Your answer (remember it!)", RegistrationForm::notEmpty);

        // ---- Step 3 : prisoner ----
        ageField = new InputField("1 - 120", RegistrationForm::isAge);
        limitToDigits(ageField, 3);
        genderBox = new SoftCombo(new String[]{"Select Gender", "Male", "Female", "Other"});
        addressField = new InputField("House no, street, city", RegistrationForm::notEmpty);
        educationField = new InputField("e.g. 10th, Diploma, B.Sc", RegistrationForm::notEmpty);
        skillsField = new InputField("e.g. Tailoring, Carpentry, Computers", RegistrationForm::notEmpty);
        arrestReasonField = new InputField("Short reason", RegistrationForm::notEmpty);
        releaseDateField = new InputField("YYYY-MM-DD  (e.g. 2025-03-15)", RegistrationForm::isPastDate);
        releaseStatusBox = new SoftCombo(new String[]{"Select Status", "Released", "On Parole"});
        employmentBox = new SoftCombo(new String[]{"Select Status", "Employed", "Unemployed", "Student"});

        counsellingYes = new JRadioButton("Yes");
        counsellingNo = new JRadioButton("No");
        counsellingGroup = new ButtonGroup();
        counsellingGroup.add(counsellingYes);
        counsellingGroup.add(counsellingNo);
        for (JRadioButton rb : new JRadioButton[]{counsellingYes, counsellingNo}) {
            rb.setOpaque(false);
            rb.setFont(INPUT_FONT);
            rb.setForeground(TEXT);
            rb.setFocusPainted(false);
            rb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // ---- Step 3 : counsellor ----
        qualificationField = new InputField("e.g. M.A. Psychology", RegistrationForm::notEmpty);
        specializationField = new InputField("e.g. Career guidance", RegistrationForm::notEmpty);
        experienceField = new InputField("Years (0 - 99)", RegistrationForm::isYears);
        limitToDigits(experienceField, 2);
        availabilityField = new InputField("e.g. Mon-Fri, 10am - 4pm", RegistrationForm::notEmpty);
        modeBox = new SoftCombo(new String[]{"Select Mode", "Online", "In-Person", "Both"});
        counsellorStatusBox = new SoftCombo(new String[]{"Select Status", "Active", "Inactive"});

        // Enter key: move to the next box / next step
        nameField.addActionListener(e -> usernameField.requestFocusInWindow());
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> emailField.requestFocusInWindow());
        emailField.addActionListener(e -> phoneField.requestFocusInWindow());
        phoneField.addActionListener(e -> nextStep());
        securityAnswerField.addActionListener(e -> nextStep());
    }

    /** Updates the progress tree and clears red messages whenever the user types or picks something. */
    private void createListeners() {

        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onFormChanged(); }
            public void removeUpdate(DocumentEvent e)  { onFormChanged(); }
            public void changedUpdate(DocumentEvent e) { onFormChanged(); }
        };

        JTextComponent[] texts = {
            nameField, usernameField, passwordField, emailField, phoneField,
            securityAnswerField, ageField, addressField, educationField, skillsField,
            arrestReasonField, releaseDateField, qualificationField,
            specializationField, experienceField, availabilityField
        };
        for (JTextComponent t : texts) {
            t.getDocument().addDocumentListener(docListener);
        }

        ActionListener comboListener = e -> onFormChanged();
        SoftCombo[] combos = {
            securityQuestionBox, genderBox, releaseStatusBox,
            employmentBox, modeBox, counsellorStatusBox
        };
        for (SoftCombo c : combos) {
            c.addActionListener(comboListener);
        }
        counsellingYes.addActionListener(comboListener);
        counsellingNo.addActionListener(comboListener);

        // password strength meter follows the password box
        passwordField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { passwordHelper.setPassword(currentPassword()); }
            public void removeUpdate(DocumentEvent e)  { passwordHelper.setPassword(currentPassword()); }
            public void changedUpdate(DocumentEvent e) { passwordHelper.setPassword(currentPassword()); }
        });
    }

    private void onFormChanged() {
        clearMessage();
        refreshProgress();
    }

    private String currentPassword() {
        return new String(passwordField.getPassword());
    }

    /** The big rounded card in the middle: hero picture on the left, form on the right. */
    private JPanel createShell() {

        ShellPanel shell = new ShellPanel();
        shell.setLayout(new BorderLayout());
        shell.setBorder(new EmptyBorder(SHADOW, SHADOW, SHADOW, SHADOW));

        hero = new HeroPanel();
        rightPanel = createRightPanel();

        // BorderLayout: the picture side has a fixed share, the form takes the rest
        shell.add(hero, BorderLayout.WEST);
        shell.add(rightPanel, BorderLayout.CENTER);

        return shell;
    }

    /** Right side: title, step indicator, pages, message, buttons. */
    private JPanel createRightPanel() {

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(26, 44, 18, 44));

        // ---- top : heading + stepper ----
        JLabel heading = new JLabel("Create your account");
        heading.setFont(new Font("Georgia", Font.BOLD, 30));
        heading.setForeground(DARK_GREEN);

        JLabel sub = new JLabel("A new beginning starts with support.");
        sub.setFont(new Font("Georgia", Font.ITALIC, 14));
        sub.setForeground(new Color(80, 105, 90));

        stepper = new Stepper(STEP_NAMES);
        stepper.setOnClick(this::jumpTo);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        stepper.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(heading);
        top.add(Box.createVerticalStrut(2));
        top.add(sub);
        top.add(Box.createVerticalStrut(8));
        top.add(stepper);
        panel.add(top, BorderLayout.NORTH);

        // ---- middle : the three pages ----
        pageLayout = new CardLayout();
        pages = new JPanel(pageLayout);
        pages.setOpaque(false);
        pages.add(buildAccountPage(), String.valueOf(STEP_ACCOUNT));
        pages.add(buildRolePage(), String.valueOf(STEP_ROLE));
        pages.add(buildDetailsPage(), String.valueOf(STEP_DETAILS));
        panel.add(pages, BorderLayout.CENTER);

        // ---- bottom : message, buttons, login link ----
        messageLabel = new JLabel(" ", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        messageLabel.setForeground(ERROR_RED);

        backButton = new RoundedButton("\u2190  Back", false);
        nextButton = new RoundedButton("Next  \u2192", true);
        clearButton = new RoundedButton("Clear", false);

        backButton.addActionListener(e -> goTo(step - 1));
        nextButton.addActionListener(e -> nextStep());
        clearButton.addActionListener(e -> clearForm());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftButtons.setBorder(new EmptyBorder(0, 0, 4, 0));
        leftButtons.setOpaque(false);
        leftButtons.add(backButton);
        leftButtons.add(clearButton);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.add(leftButtons, BorderLayout.WEST);
        buttonRow.add(nextButton, BorderLayout.EAST);

        JLabel loginLink = new JLabel(
            "<html>Already have an account? <b><u>Log in</u></b></html>", SwingConstants.CENTER);
        loginLink.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        loginLink.setForeground(DARK_GREEN);
        loginLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!busy) {
                    dispose();
                    new LoginForm();          // LoginForm shows itself
                }
            }
        });

        messageLabel.setPreferredSize(new Dimension(100, 26));
        loginLink.setBorder(new EmptyBorder(10, 0, 0, 0));

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.add(messageLabel, BorderLayout.NORTH);
        bottom.add(buttonRow, BorderLayout.CENTER);
        bottom.add(loginLink, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // ---------------------------------------------------------------------
    //  Page 1 : account
    // ---------------------------------------------------------------------
    private JPanel buildAccountPage() {

        JPanel page = pagePanel();

        put(page, labeled("Full Name", nameField), 0, 0, 1);
        put(page, labeled("Username", usernameField), 1, 0, 1);

        // password row has a Show / Hide link on the right
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(LABEL_FONT);
        passLabel.setForeground(TEXT);

        JLabel showLink = new JLabel("Show");
        showLink.setFont(new Font("Segoe UI", Font.BOLD, 12));
        showLink.setForeground(LIGHT_GREEN);
        showLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean hidden = passwordField.getEchoChar() != (char) 0;
                passwordField.setEchoChar(hidden ? (char) 0 : '\u2022');
                showLink.setText(hidden ? "Hide" : "Show");
            }
        });

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.add(passLabel, BorderLayout.WEST);
        labelRow.add(showLink, BorderLayout.EAST);

        JPanel passBlock = new JPanel(new BorderLayout(0, 5));
        passBlock.setOpaque(false);
        passBlock.add(labelRow, BorderLayout.NORTH);
        passBlock.add(passwordField, BorderLayout.CENTER);
        passBlock.add(passwordHelper, BorderLayout.SOUTH);
        put(page, passBlock, 0, 1, 2);

        put(page, labeled("Gmail Address", emailField), 0, 2, 1);
        put(page, labeled("Phone Number", phoneField), 1, 2, 1);

        addFiller(page, 3);
        return page;
    }

    // ---------------------------------------------------------------------
    //  Page 2 : security question + role cards
    // ---------------------------------------------------------------------
    private JPanel buildRolePage() {

        JPanel page = pagePanel();

        put(page, labeled("Security Question", securityQuestionBox), 0, 0, 2);
        put(page, labeled("Security Answer", securityAnswerField), 0, 1, 2);

        roleCards = new RoleCard[]{
            new RoleCard("PRISONER", "Prisoner",
                    "Rebuild my life after release", RoleCard.ICON_SUNRISE),
            new RoleCard("COUNSELLOR", "Counsellor",
                    "Guide and support others", RoleCard.ICON_HEART),
            new RoleCard("NGO", "NGO",
                    "Offer schemes and jobs", RoleCard.ICON_GLOBE)
        };

        JPanel cardRow = new JPanel(new GridLayout(1, 3, 12, 0));
        cardRow.setOpaque(false);
        for (RoleCard rc : roleCards) {
            rc.setOnClick(this::selectRole);
            cardRow.add(rc);
        }
        put(page, labeled("I am joining as", cardRow), 0, 2, 2);

        addFiller(page, 3);
        return page;
    }

    // ---------------------------------------------------------------------
    //  Page 3 : details (filled in by rebuildDetailsPage)
    // ---------------------------------------------------------------------
    private JScrollPane buildDetailsPage() {

        detailsPanel = new TrackWidthPanel(new GridBagLayout());
        detailsPanel.setOpaque(false);
        rebuildDetailsPage();

        JScrollPane scroll = new JScrollPane(detailsPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        return scroll;
    }

    /** Shows the right fields for the chosen role. */
    private void rebuildDetailsPage() {

        detailsPanel.removeAll();
        int row = 0;

        if ("PRISONER".equals(selectedRole)) {

            addHeading("PRISONER DETAILS", "Tell us a little about yourself so we can match the right help.");
            row = 1;
            put(detailsPanel, labeled("Age", ageField), 0, row, 1);
            put(detailsPanel, labeled("Gender", genderBox), 1, row++, 1);
            put(detailsPanel, labeled("Address", addressField), 0, row++, 2);
            put(detailsPanel, labeled("Education Qualification", educationField), 0, row, 1);
            put(detailsPanel, labeled("Skills", skillsField), 1, row++, 1);
            put(detailsPanel, labeled("Reason for Arrest", arrestReasonField), 0, row++, 2);
            put(detailsPanel, labeled("Release Date", releaseDateField), 0, row, 1);
            put(detailsPanel, labeled("Release Status", releaseStatusBox), 1, row++, 1);
            put(detailsPanel, labeled("Employment Status", employmentBox), 0, row, 1);

            JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 9));
            radios.setOpaque(false);
            radios.add(counsellingYes);
            radios.add(counsellingNo);
            put(detailsPanel, labeled("Counselling Required", radios), 1, row++, 1);

        } else if ("COUNSELLOR".equals(selectedRole)) {

            addHeading("COUNSELLOR DETAILS", "Share your experience so people can find the right guide.");
            row = 1;
            put(detailsPanel, labeled("Qualification", qualificationField), 0, row, 1);
            put(detailsPanel, labeled("Specialization", specializationField), 1, row++, 1);
            put(detailsPanel, labeled("Years of Experience", experienceField), 0, row, 1);
            put(detailsPanel, labeled("Availability", availabilityField), 1, row++, 1);
            put(detailsPanel, labeled("Counselling Mode", modeBox), 0, row, 1);
            put(detailsPanel, labeled("Status", counsellorStatusBox), 1, row++, 1);

        } else if ("NGO".equals(selectedRole)) {

            addHeading("NGO ACCOUNT", "Nothing more to fill in.");
            JLabel info = new JLabel("<html><body style='width:380px'>"
                + "You are all set! Press <b>CREATE ACCOUNT</b> and you can start sharing "
                + "government schemes and job opportunities that help people rebuild their lives."
                + "</body></html>");
            info.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            info.setForeground(TEXT);
            info.setBorder(new EmptyBorder(14, 0, 0, 0));
            put(detailsPanel, info, 0, 1, 2);
            row = 2;

        } else {
            addHeading("YOUR DETAILS", "Please choose a role in the previous step.");
            row = 1;
        }

        addFiller(detailsPanel, row);
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    private void addHeading(String title, String subtitle) {

        JLabel t = new JLabel(title);
        t.setFont(new Font("Georgia", Font.BOLD, 18));
        t.setForeground(DARK_GREEN);

        JLabel s = new JLabel(subtitle);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        s.setForeground(new Color(95, 115, 105));

        JPanel p = new JPanel(new GridLayout(2, 1, 0, 1));
        p.setOpaque(false);
        p.add(t);
        p.add(s);
        put(detailsPanel, p, 0, 0, 2);
    }

    // ---------------------------------------------------------------------
    //  Small layout helpers
    // ---------------------------------------------------------------------
    private JPanel pagePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        return p;
    }

    /** Puts a small label above a component. */
    private JPanel labeled(String text, JComponent comp) {
        JLabel l = new JLabel(text);
        l.setFont(LABEL_FONT);
        l.setForeground(TEXT);

        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /** Adds a component to a GridBag page: column x, row y, spanning 1 or 2 columns. */
    private void put(JPanel page, JComponent c, int x, int y, int span) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.gridwidth = span;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH;
        g.insets = new Insets(6, x == 0 ? 0 : 8, 6, (x == 0 && span == 1) ? 8 : 0);
        page.add(c, g);
    }

    /** Pushes everything to the top of the page. */
    private void addFiller(JPanel page, int row) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        g.weighty = 1;
        g.fill = GridBagConstraints.VERTICAL;
        page.add(Box.createGlue(), g);
    }

    /** Allows only digits and a maximum length in a text box. */
    private void limitToDigits(JTextField field, int maxLength) {
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(new DigitsFilter(maxLength));
    }

    // =====================================================================
    //  5. WIZARD NAVIGATION
    // =====================================================================

    /** Shows one page and updates buttons, stepper and progress. */
    private void goTo(int newStep) {

        if (newStep < STEP_ACCOUNT || newStep > STEP_DETAILS) {
            return;
        }
        step = newStep;
        pageLayout.show(pages, String.valueOf(step));
        stepper.setStep(step);
        clearMessage();

        backButton.setVisible(step > STEP_ACCOUNT);
        nextButton.setText(step == STEP_DETAILS ? "CREATE ACCOUNT" : "Next  \u2192");
        refreshProgress();
    }

    /** Clicking a step circle: only allowed to go to steps already visited. */
    private void jumpTo(int target) {
        if (!busy) {
            goTo(target);
        }
    }

    /** Next button: validate the current page, then move on (or register on the last page). */
    private void nextStep() {

        if (busy) {
            return;
        }
        if (!validatePage(step)) {
            return;
        }
        if (step < STEP_DETAILS) {
            goTo(step + 1);
        } else {
            registerUser();
        }
    }

    private void selectRole(String role) {
        selectedRole = role;
        for (RoleCard rc : roleCards) {
            rc.setSelected(rc.getRole().equals(role));
        }
        rebuildDetailsPage();
        onFormChanged();
    }

    // =====================================================================
    //  6. VALIDATION  (same rules and messages as the old form)
    // =====================================================================

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean isAge(String s) {
        try {
            int age = Integer.parseInt(s.trim());
            return age >= 1 && age <= 120;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isYears(String s) {
        try {
            int years = Integer.parseInt(s.trim());
            return years >= 0 && years <= 99;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isPastDate(String s) {
        try {
            return !LocalDate.parse(s.trim()).isAfter(LocalDate.now());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean validatePage(int page) {
        switch (page) {
            case STEP_ACCOUNT: return validateAccountPage();
            case STEP_ROLE:    return validateRolePage();
            default:           return validateDetailsPage();
        }
    }

    /** Shows a red message under the form and shakes the card. */
    private boolean fail(String message, JComponent... bad) {
        for (JComponent c : bad) {
            if (c instanceof InputField) {
                ((InputField) c).flagError();
            } else if (c instanceof PassField) {
                ((PassField) c).flagError();
            } else if (c instanceof SoftCombo) {
                ((SoftCombo) c).flagError();
            }
        }
        messageLabel.setText(message);
        shake();
        return false;
    }

    private void clearMessage() {
        if (messageLabel != null && !messageLabel.getText().trim().isEmpty()) {
            messageLabel.setText(" ");
        }
    }

    private boolean validateAccountPage() {

        String name = nameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = currentPassword();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()
                || email.isEmpty() || phone.isEmpty()) {
            return fail("Please fill in all common fields.",
                    nameField.getText().trim().isEmpty() ? nameField : null,
                    username.isEmpty() ? usernameField : null,
                    password.isEmpty() ? passwordField : null,
                    email.isEmpty() ? emailField : null,
                    phone.isEmpty() ? phoneField : null);
        }
        if (!password.matches(PASSWORD_REGEX)) {
            return fail("Password must contain at least 8 characters, "
                    + "one number and one special character.", passwordField);
        }
        if (!email.matches(EMAIL_REGEX)) {
            return fail("Enter a valid Gmail address.", emailField);
        }
        if (!phone.matches(PHONE_REGEX)) {
            return fail("Phone number must contain exactly 10 digits.", phoneField);
        }
        return true;
    }

    private boolean validateRolePage() {

        if (selectedRole == null) {
            for (RoleCard rc : roleCards) {
                rc.flashError();
            }
            return fail("Please select a role.");
        }
        if (securityQuestionBox.getSelectedIndex() == 0) {
            return fail("Please select a security question.", securityQuestionBox);
        }
        if (securityAnswerField.getText().trim().isEmpty()) {
            return fail("Please fill in all common fields.", securityAnswerField);
        }
        return true;
    }

    private boolean validateDetailsPage() {

        if ("PRISONER".equals(selectedRole)) {

            if (ageField.getText().trim().isEmpty()
                    || addressField.getText().trim().isEmpty()
                    || educationField.getText().trim().isEmpty()
                    || skillsField.getText().trim().isEmpty()
                    || arrestReasonField.getText().trim().isEmpty()
                    || releaseDateField.getText().trim().isEmpty()
                    || genderBox.getSelectedIndex() == 0
                    || releaseStatusBox.getSelectedIndex() == 0
                    || employmentBox.getSelectedIndex() == 0
                    || (!counsellingYes.isSelected() && !counsellingNo.isSelected())) {

                return fail("Please complete all Prisoner details.",
                        ageField.getText().trim().isEmpty() ? ageField : null,
                        addressField.getText().trim().isEmpty() ? addressField : null,
                        educationField.getText().trim().isEmpty() ? educationField : null,
                        skillsField.getText().trim().isEmpty() ? skillsField : null,
                        arrestReasonField.getText().trim().isEmpty() ? arrestReasonField : null,
                        releaseDateField.getText().trim().isEmpty() ? releaseDateField : null,
                        genderBox.getSelectedIndex() == 0 ? genderBox : null,
                        releaseStatusBox.getSelectedIndex() == 0 ? releaseStatusBox : null,
                        employmentBox.getSelectedIndex() == 0 ? employmentBox : null);
            }
            if (!isAge(ageField.getText())) {
                try {
                    Integer.parseInt(ageField.getText().trim());
                    return fail("Age must be between 1 and 120.", ageField);
                } catch (NumberFormatException ex) {
                    return fail("Age must be a valid number.", ageField);
                }
            }
            try {
                LocalDate date = LocalDate.parse(releaseDateField.getText().trim());
                if (date.isAfter(LocalDate.now())) {
                    return fail("Release date cannot be in the future.", releaseDateField);
                }
            } catch (Exception ex) {
                return fail("Enter release date as YYYY-MM-DD.", releaseDateField);
            }

        } else if ("COUNSELLOR".equals(selectedRole)) {

            if (qualificationField.getText().trim().isEmpty()
                    || specializationField.getText().trim().isEmpty()
                    || experienceField.getText().trim().isEmpty()
                    || availabilityField.getText().trim().isEmpty()
                    || modeBox.getSelectedIndex() == 0
                    || counsellorStatusBox.getSelectedIndex() == 0) {

                return fail("Please complete all Counsellor details.",
                        qualificationField.getText().trim().isEmpty() ? qualificationField : null,
                        specializationField.getText().trim().isEmpty() ? specializationField : null,
                        experienceField.getText().trim().isEmpty() ? experienceField : null,
                        availabilityField.getText().trim().isEmpty() ? availabilityField : null,
                        modeBox.getSelectedIndex() == 0 ? modeBox : null,
                        counsellorStatusBox.getSelectedIndex() == 0 ? counsellorStatusBox : null);
            }
            if (!isYears(experienceField.getText())) {
                try {
                    Integer.parseInt(experienceField.getText().trim());
                    return fail("Experience must be between 0 and 99 years.", experienceField);
                } catch (NumberFormatException ex) {
                    return fail("Experience must be a valid number.", experienceField);
                }
            }
        }
        return true;
    }

    // =====================================================================
    //  7. PROGRESS (drives the growing sapling)
    // =====================================================================

    /** Counts how many fields are correctly filled and tells the tree to grow. */
    private void refreshProgress() {

        if (hero == null || passwordField == null) {
            return;
        }

        int done = 0;
        int total = 8;                       // common part

        if (notEmpty(nameField.getText()))                done++;
        if (notEmpty(usernameField.getText()))            done++;
        if (currentPassword().matches(PASSWORD_REGEX))    done++;
        if (emailField.getText().trim().matches(EMAIL_REGEX)) done++;
        if (phoneField.getText().trim().matches(PHONE_REGEX)) done++;
        if (selectedRole != null)                         done++;
        if (securityQuestionBox.getSelectedIndex() > 0)   done++;
        if (notEmpty(securityAnswerField.getText()))      done++;

        String role = selectedRole == null ? "PRISONER" : selectedRole;

        if ("PRISONER".equals(role)) {
            total += 10;
            if (isAge(ageField.getText()))                done++;
            if (genderBox.getSelectedIndex() > 0)         done++;
            if (notEmpty(addressField.getText()))         done++;
            if (notEmpty(educationField.getText()))       done++;
            if (notEmpty(skillsField.getText()))          done++;
            if (notEmpty(arrestReasonField.getText()))    done++;
            if (isPastDate(releaseDateField.getText()))   done++;
            if (releaseStatusBox.getSelectedIndex() > 0)  done++;
            if (employmentBox.getSelectedIndex() > 0)     done++;
            if (counsellingYes.isSelected() || counsellingNo.isSelected()) done++;
        } else if ("COUNSELLOR".equals(role)) {
            total += 6;
            if (notEmpty(qualificationField.getText()))   done++;
            if (notEmpty(specializationField.getText()))  done++;
            if (isYears(experienceField.getText()))       done++;
            if (notEmpty(availabilityField.getText()))    done++;
            if (modeBox.getSelectedIndex() > 0)           done++;
            if (counsellorStatusBox.getSelectedIndex() > 0) done++;
        }

        hero.setTarget((double) done / total);
    }

    // =====================================================================
    //  8. SHAKE ANIMATION (used when something is wrong)
    // =====================================================================
    private void shake() {
        // Animation removed: the red message and red field borders are enough.
    }

    // =====================================================================
    //  9. CLEAR
    // =====================================================================
    private void clearForm() {

        if (busy) {
            return;
        }

        nameField.reset();
        usernameField.reset();
        passwordField.reset();
        emailField.reset();
        phoneField.reset();
        securityAnswerField.reset();
        securityQuestionBox.setSelectedIndex(0);

        ageField.reset();
        genderBox.setSelectedIndex(0);
        addressField.reset();
        educationField.reset();
        skillsField.reset();
        arrestReasonField.reset();
        releaseDateField.reset();
        releaseStatusBox.setSelectedIndex(0);
        employmentBox.setSelectedIndex(0);
        counsellingGroup.clearSelection();

        qualificationField.reset();
        specializationField.reset();
        experienceField.reset();
        availabilityField.reset();
        modeBox.setSelectedIndex(0);
        counsellorStatusBox.setSelectedIndex(0);

        selectedRole = null;
        for (RoleCard rc : roleCards) {
            rc.setSelected(false);
        }
        rebuildDetailsPage();

        stepper.reset();
        goTo(STEP_ACCOUNT);
        nameField.requestFocusInWindow();
    }

    // =====================================================================
    //  10. DATABASE  (SQL and tables are exactly the same as before)
    // =====================================================================

    /** A simple box that carries every value typed by the user. */
    private static class FormData {
        String name, username, password, email, phone, role;
        String question, answer;

        int age;
        String gender, address, education, skills, arrestReason;
        String releaseDate, releaseStatus, employment, counselling;

        String qualification, specialization, availability, mode, counsellorStatus;
        int experienceYears;
    }

    /** Thrown when we cannot even connect to Oracle. */
    private static class DbConnectException extends Exception {
        DbConnectException(String message) {
            super(message);
        }
    }

    /** Reads all values from the screen (runs on the screen thread). */
    private FormData collectData() {

        FormData d = new FormData();
        d.name = nameField.getText().trim();
        d.username = usernameField.getText().trim();
        d.password = currentPassword();
        d.email = emailField.getText().trim();
        d.phone = phoneField.getText().trim();
        d.role = selectedRole;
        d.question = (String) securityQuestionBox.getSelectedItem();
        d.answer = securityAnswerField.getText().trim();

        if ("PRISONER".equals(selectedRole)) {
            d.age = Integer.parseInt(ageField.getText().trim());
            d.gender = (String) genderBox.getSelectedItem();
            d.address = addressField.getText().trim();
            d.education = educationField.getText().trim();
            d.skills = skillsField.getText().trim();
            d.arrestReason = arrestReasonField.getText().trim();
            d.releaseDate = releaseDateField.getText().trim();
            d.releaseStatus = (String) releaseStatusBox.getSelectedItem();
            d.employment = (String) employmentBox.getSelectedItem();
            d.counselling = counsellingYes.isSelected() ? "Yes" : "No";

        } else if ("COUNSELLOR".equals(selectedRole)) {
            d.qualification = qualificationField.getText().trim();
            d.specialization = specializationField.getText().trim();
            d.experienceYears = Integer.parseInt(experienceField.getText().trim());
            d.availability = availabilityField.getText().trim();
            d.mode = (String) modeBox.getSelectedItem();
            d.counsellorStatus = (String) counsellorStatusBox.getSelectedItem();
        }
        return d;
    }

    /** Check everything again, then save in the background so the window never freezes. */
    private void registerUser() {

        // safety check: every page must be valid
        for (int p = STEP_ACCOUNT; p <= STEP_DETAILS; p++) {
            if (!validatePage(p)) {
                goTo(p);
                return;
            }
        }

        final FormData data = collectData();
        setBusy(true);

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                saveToDatabase(data);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();                                   // throws if saving failed
                    hero.setTarget(1.0);
                    JOptionPane.showMessageDialog(RegistrationForm.this,
                            "Account created successfully! Please log in.");
                    dispose();
                    new LoginForm().setVisible(true);

                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showSaveError(cause);
                }
            }
        }.execute();
    }

    private void showSaveError(Throwable cause) {

        String message = cause.getMessage() == null ? "" : cause.getMessage();

        if (cause instanceof DbConnectException) {
            JOptionPane.showMessageDialog(this, "Could not connect to Oracle: " + message,
                    "Registration", JOptionPane.WARNING_MESSAGE);

        } else if (message.toUpperCase().contains("ORA-00001")) {
            goTo(STEP_ACCOUNT);
            usernameField.flagError();
            fail("Username already exists. Choose another.", usernameField);
            usernameField.requestFocusInWindow();

        } else {
            JOptionPane.showMessageDialog(this, "Registration failed: " + message,
                    "Registration", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        nextButton.setEnabled(!value);
        backButton.setEnabled(!value);
        clearButton.setEnabled(!value);
        nextButton.setText(value ? "Creating account..." : "CREATE ACCOUNT");
        setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    /** Saves the user (and the role details) in ONE transaction. */
    private void saveToDatabase(FormData d) throws Exception {

        String sql = "INSERT INTO USERS "
                + "(user_id, full_name, username, password, email, phone, "
                + "role, security_question, security_answer) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection con;
        try {
            con = DBConnection.getConnection();
        } catch (SQLException ex) {
            throw new DbConnectException(ex.getMessage());
        }

        try {
            con.setAutoCommit(false);

            try {
                int userId = nextId(con, "USERS", "user_id");

                String hashedPassword = PasswordUtil.hashPassword(d.password);

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, d.name);
                    ps.setString(3, d.username);
                    ps.setString(4, hashedPassword);
                    ps.setString(5, d.email);
                    ps.setString(6, d.phone);
                    ps.setString(7, d.role);
                    ps.setString(8, d.question);
                    ps.setString(9, d.answer);
                    ps.executeUpdate();
                }

                if ("PRISONER".equals(d.role)) {
                    insertPrisoner(con, userId, d);
                } else if ("COUNSELLOR".equals(d.role)) {
                    insertCounsellor(con, userId, d);
                }

                con.commit();

            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }

        } finally {
            con.close();
        }
    }

    private int nextId(Connection con, String table, String column) throws SQLException {

        String sql = "SELECT NVL(MAX(" + column + "), 0) + 1 FROM " + table;

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertPrisoner(Connection con, int userId, FormData d) throws SQLException {

        String sql = "INSERT INTO PRISONER_DETAILS "
                + "(prisoner_id, user_id, age, gender, address, education, "
                + "skills, arrest_reason, release_date, release_status, "
                + "employment_status, counselling_required) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int prisonerId = nextId(con, "PRISONER_DETAILS", "prisoner_id");

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prisonerId);
            ps.setInt(2, userId);
            ps.setInt(3, d.age);
            ps.setString(4, d.gender);
            ps.setString(5, d.address);
            ps.setString(6, d.education);
            ps.setString(7, d.skills);
            ps.setString(8, d.arrestReason);
            ps.setDate(9, Date.valueOf(d.releaseDate));
            ps.setString(10, d.releaseStatus);
            ps.setString(11, d.employment);
            ps.setString(12, d.counselling);
            ps.executeUpdate();
        }
    }

    private void insertCounsellor(Connection con, int userId, FormData d) throws SQLException {

        String sql = "INSERT INTO COUNSELLOR_DETAILS "
                + "(counsellor_id, user_id, qualification, specialization, "
                + "experience_years, phone, availability, counselling_mode, "
                + "status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int counsellorId = nextId(con, "COUNSELLOR_DETAILS", "counsellor_id");

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, counsellorId);
            ps.setInt(2, userId);
            ps.setString(3, d.qualification);
            ps.setString(4, d.specialization);
            ps.setInt(5, d.experienceYears);
            ps.setString(6, d.phone);
            ps.setString(7, d.availability);
            ps.setString(8, d.mode);
            ps.setString(9, d.counsellorStatus);
            ps.executeUpdate();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RegistrationForm::new);
    }

    // =====================================================================
    //  11. DRAWING AND CUSTOM COMPONENTS
    //      (all the pretty parts live below; the logic above does not depend on them)
    // =====================================================================

    /** Small drawing helpers shared by the components. */
    private static final class Fx {

        static Graphics2D smooth(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            return g2;
        }

        static Color blend(Color a, Color b, float t) {
            t = Math.max(0, Math.min(1, t));
            return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
        }

        /** Rounded white box with a border: status 0 = normal, 1 = correct, 2 = wrong. */
        static void paintBox(Graphics g, JComponent c, boolean focused, int status) {
            Graphics2D g2 = smooth(g);
            int w = c.getWidth();
            int h = c.getHeight();

            if (focused) {
                g2.setColor(new Color(24, 120, 76, 45));
                g2.setStroke(new BasicStroke(4f));
                g2.draw(new RoundRectangle2D.Double(2, 2, w - 5, h - 5, 14, 14));
            }

            g2.setColor(status == 2 ? new Color(255, 245, 245) : FIELD_BG);
            g2.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 14, 14));

            Color border = BORDER;
            if (status == 2) {
                border = ERROR_RED;
            } else if (focused) {
                border = LIGHT_GREEN;
            } else if (status == 1) {
                border = new Color(125, 185, 145);
            }
            g2.setColor(border);
            g2.setStroke(new BasicStroke(focused || status == 2 ? 1.8f : 1.2f));
            g2.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 14, 14));
            g2.dispose();
        }

        /** Grey hint text + green tick / red mark on the right side. */
        static void paintHintAndIcon(Graphics g, JTextComponent c, String hint, int status) {
            Graphics2D g2 = smooth(g);
            int w = c.getWidth();
            int h = c.getHeight();

            if (c.getText().isEmpty() && hint != null) {
                g2.setFont(c.getFont());
                g2.setColor(HINT);
                FontMetrics fm = g2.getFontMetrics();
                int y = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(hint, c.getInsets().left, y);
            }

            int cx = w - 22;
            int cy = h / 2;
            if (status == 1) {
                g2.setColor(OK_GREEN);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Path2D.Double() {{
                    moveTo(cx - 5, cy);
                    lineTo(cx - 1, cy + 4);
                    lineTo(cx + 6, cy - 4);
                }});
            } else if (status == 2) {
                g2.setColor(ERROR_RED);
                g2.fill(new Ellipse2D.Double(cx - 8, cy - 8, 16, 16));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(cx, cy - 4, cx, cy + 1));
                g2.fill(new Ellipse2D.Double(cx - 1.2, cy + 3.2, 2.4, 2.4));
            }
            g2.dispose();
        }

        /** 0 = neutral, 1 = correct, 2 = wrong. */
        static int status(String text, Predicate<String> rule, boolean touched, boolean forced) {
            if (forced) {
                return 2;
            }
            if (rule == null || text.isEmpty()) {
                return 0;
            }
            if (rule.test(text)) {
                return 1;
            }
            return touched ? 2 : 0;
        }

        /** Draws text that wraps inside a maximum width; returns the y after the last line. */
        static int drawWrapped(Graphics2D g2, String text, int x, int y, int maxW, int lineH) {
            FontMetrics fm = g2.getFontMetrics();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(test) > maxW && line.length() > 0) {
                    g2.drawString(line.toString(), x, y);
                    y += lineH;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) {
                g2.drawString(line.toString(), x, y);
                y += lineH;
            }
            return y;
        }

        /** Loads a picture from src/rebirth or from the class path. */
        static BufferedImage loadImage(String... names) {
            for (String name : names) {
                try {
                    File file = new File("src/rebirth/" + name);
                    if (file.exists()) {
                        return ImageIO.read(file);
                    }
                    URL url = RegistrationForm.class.getResource("/rebirth/" + name);
                    if (url != null) {
                        return ImageIO.read(url);
                    }
                } catch (Exception ex) {
                    System.out.println("Image could not be loaded: " + name);
                }
            }
            return null;
        }

        /** Scales an image to cover a rectangle (like CSS background-size: cover). */
        static BufferedImage cover(BufferedImage src, int w, int h) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
            int sw = (int) (src.getWidth() * scale);
            int sh = (int) (src.getHeight() * scale);
            g2.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
            g2.dispose();
            return out;
        }
    }

    // ---------------------------------------------------------------------
    //  Text boxes
    // ---------------------------------------------------------------------

    /** Rounded text box with hint text and a live tick / cross. */
    private static class InputField extends JTextField {

        private final String hint;
        private final Predicate<String> rule;
        private boolean touched = false;
        private boolean forced = false;

        InputField(String hint, Predicate<String> rule) {
            this.hint = hint;
            this.rule = rule;
            setOpaque(false);
            setFont(INPUT_FONT);
            setForeground(TEXT);
            setCaretColor(DARK_GREEN);
            setBorder(new EmptyBorder(8, 14, 8, 38));
            setPreferredSize(new Dimension(100, 42));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { repaint(); }

                @Override
                public void focusLost(FocusEvent e) {
                    touched = true;
                    repaint();
                }
            });
            getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e)  { forced = false; repaint(); }
                public void removeUpdate(DocumentEvent e)  { forced = false; repaint(); }
                public void changedUpdate(DocumentEvent e) { forced = false; repaint(); }
            });
        }

        void flagError() {
            forced = true;
            repaint();
        }

        void reset() {
            setText("");
            touched = false;
            forced = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int status = Fx.status(getText(), rule, touched, forced);
            Fx.paintBox(g, this, hasFocus(), status);
            super.paintComponent(g);
            Fx.paintHintAndIcon(g, this, hint, status);
        }
    }

    /** Same as InputField but hides the typed characters. */
    private static class PassField extends JPasswordField {

        private final String hint;
        private final Predicate<String> rule;
        private boolean touched = false;
        private boolean forced = false;

        PassField(String hint, Predicate<String> rule) {
            this.hint = hint;
            this.rule = rule;
            setOpaque(false);
            setFont(INPUT_FONT);
            setForeground(TEXT);
            setCaretColor(DARK_GREEN);
            setEchoChar('\u2022');
            setBorder(new EmptyBorder(8, 14, 8, 38));
            setPreferredSize(new Dimension(100, 42));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { repaint(); }

                @Override
                public void focusLost(FocusEvent e) {
                    touched = true;
                    repaint();
                }
            });
            getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e)  { forced = false; repaint(); }
                public void removeUpdate(DocumentEvent e)  { forced = false; repaint(); }
                public void changedUpdate(DocumentEvent e) { forced = false; repaint(); }
            });
        }

        void flagError() {
            forced = true;
            repaint();
        }

        void reset() {
            setText("");
            touched = false;
            forced = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int status = Fx.status(new String(getPassword()), rule, touched, forced);
            Fx.paintBox(g, this, hasFocus(), status);
            super.paintComponent(g);
            Fx.paintHintAndIcon(g, this, hint, status);
        }
    }

    /** Lets a text box accept only digits, up to a maximum length. */
    private static class DigitsFilter extends DocumentFilter {

        private final int max;

        DigitsFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet a)
                throws BadLocationException {
            replace(fb, offset, 0, text, a);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet a)
                throws BadLocationException {
            String digits = text == null ? "" : text.replaceAll("\\D", "");
            int newLength = fb.getDocument().getLength() - length + digits.length();
            if (newLength <= max) {
                super.replace(fb, offset, length, digits, a);
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Drop-down list
    // ---------------------------------------------------------------------

    /** A rounded wrapper around a JComboBox so it matches the text boxes. */
    private static class SoftCombo extends JPanel {

        private final JComboBox<String> box;
        private boolean forced = false;

        SoftCombo(String[] items) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(3, 10, 3, 6));
            setPreferredSize(new Dimension(100, 42));

            box = new JComboBox<>(items);
            box.setUI(new BasicComboBoxUI() {

                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = Fx.smooth(g);
                            g2.setColor(LIGHT_GREEN);
                            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND));
                            int cx = getWidth() / 2;
                            int cy = getHeight() / 2;
                            g2.draw(new Line2D.Double(cx - 5, cy - 2, cx, cy + 3));
                            g2.draw(new Line2D.Double(cx, cy + 3, cx + 5, cy - 2));
                            g2.dispose();
                        }
                    };
                    b.setContentAreaFilled(false);
                    b.setBorderPainted(false);
                    b.setFocusPainted(false);
                    b.setPreferredSize(new Dimension(28, 28));
                    return b;
                }

                // no blue selection colour behind the chosen item
                @Override
                public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                }

                @Override
                public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                    Object value = comboBox.getSelectedItem();
                    if (value == null) {
                        return;
                    }
                    Component c = comboBox.getRenderer().getListCellRendererComponent(
                            listBox, value, -1, false, false);
                    c.setFont(comboBox.getFont());
                    currentValuePane.paintComponent(g, c, comboBox,
                            bounds.x, bounds.y, bounds.width, bounds.height, false);
                }
            });
            box.setFont(INPUT_FONT);
            box.setForeground(TEXT);
            box.setOpaque(false);
            box.setBorder(null);
            box.setMaximumRowCount(6);
            box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean selected, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(
                            list, value, index, selected, focus);
                    l.setBorder(new EmptyBorder(6, 8, 6, 8));
                    l.setFont(INPUT_FONT);
                    if (index == -1) {
                        l.setOpaque(false);
                        boolean placeholder = box.getSelectedIndex() == 0;
                        l.setForeground(placeholder ? HINT : TEXT);
                    } else {
                        l.setOpaque(true);
                        l.setBackground(selected ? new Color(222, 240, 230) : Color.WHITE);
                        l.setForeground(index == 0 ? HINT : TEXT);
                    }
                    return l;
                }
            });
            box.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { repaint(); }

                @Override
                public void focusLost(FocusEvent e) { repaint(); }
            });
            box.addActionListener(e -> {
                forced = false;
                repaint();
            });
            add(box, BorderLayout.CENTER);
        }

        Object getSelectedItem()      { return box.getSelectedItem(); }
        int getSelectedIndex()        { return box.getSelectedIndex(); }
        void setSelectedIndex(int i)  { box.setSelectedIndex(i); }
        void addActionListener(ActionListener l) { box.addActionListener(l); }

        void flagError() {
            forced = true;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int status = forced ? 2 : (box.getSelectedIndex() > 0 ? 1 : 0);
            Fx.paintBox(g, this, box.hasFocus() || box.isPopupVisible(), status);
        }
    }

    // ---------------------------------------------------------------------
    //  Buttons
    // ---------------------------------------------------------------------

    /** Rounded button with a smooth hover animation. */
    private static class RoundedButton extends JButton {

        private final boolean filled;
        private final float hover = 0f;   // no hover animation

        RoundedButton(String text, boolean filled) {
            super(text);
            this.filled = filled;
            setFont(new Font("Segoe UI", Font.BOLD, 15));
            setForeground(filled ? Color.WHITE : DARK_GREEN);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(11, 24, 11, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Fx.smooth(g);
            int w = getWidth();
            int h = getHeight();
            boolean on = isEnabled();

            if (filled) {
                Color top = Fx.blend(LIGHT_GREEN, new Color(44, 150, 98), hover);
                Color bottom = Fx.blend(DARK_GREEN, new Color(20, 100, 66), hover);
                if (!on) {
                    top = new Color(150, 170, 158);
                    bottom = new Color(130, 150, 138);
                }
                if (getModel().isPressed()) {
                    top = top.darker();
                    bottom = bottom.darker();
                }
                g2.setColor(new Color(0, 0, 0, (int) (20 + 25 * hover)));
                g2.fill(new RoundRectangle2D.Double(1, 4, w - 2, h - 4, h, h));
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h - 3, h, h));
            } else {
                g2.setColor(Fx.blend(SOFT_WHITE, new Color(226, 242, 232), hover));
                g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 3, h, h));
                g2.setColor(Fx.blend(BORDER, LIGHT_GREEN, hover));
                g2.setStroke(new BasicStroke(1.3f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 4, h, h));
                setForeground(on ? DARK_GREEN : HINT);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------------
    //  Step indicator
    // ---------------------------------------------------------------------

    /** The 1 - 2 - 3 progress dots at the top of the form. */
    private static class Stepper extends JComponent {

        private final String[] labels;
        private int current = 0;
        private int maxReached = 0;
        private double pos = 0;
        private Consumer<Integer> onClick;

        Stepper(String[] labels) {
            this.labels = labels;
            setPreferredSize(new Dimension(100, 66));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = hit(e.getPoint());
                    if (i >= 0 && i <= maxReached && i != current && onClick != null) {
                        onClick.accept(i);
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = hit(e.getPoint());
                    boolean clickable = i >= 0 && i <= maxReached && i != current;
                    setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                        : Cursor.getDefaultCursor());
                }
            });
        }

        void setOnClick(Consumer<Integer> onClick) {
            this.onClick = onClick;
        }

        void setStep(int step) {
            current = step;
            maxReached = Math.max(maxReached, step);
            pos = step;
            repaint();
        }

        void reset() {
            maxReached = 0;
        }

        private int nodeX(int i) {
            int margin = 70;
            int usable = Math.max(1, getWidth() - 2 * margin);
            return margin + usable * i / (labels.length - 1);
        }

        private int hit(Point p) {
            for (int i = 0; i < labels.length; i++) {
                if (Math.abs(p.x - nodeX(i)) <= 18 && p.y < 46) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Fx.smooth(g);
            int cy = 20;
            int r = 15;

            // connecting lines
            for (int i = 0; i < labels.length - 1; i++) {
                int x1 = nodeX(i) + r + 4;
                int x2 = nodeX(i + 1) - r - 4;
                g2.setColor(new Color(214, 220, 210));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x1, cy, x2, cy);

                double fill = Math.max(0, Math.min(1, pos - i));
                if (fill > 0) {
                    g2.setColor(LIGHT_GREEN);
                    g2.drawLine(x1, cy, x1 + (int) ((x2 - x1) * fill), cy);
                }
            }

            // circles and labels
            for (int i = 0; i < labels.length; i++) {
                int cx = nodeX(i);
                boolean done = i < current;
                boolean active = i == current;

                if (active) {
                    g2.setColor(new Color(24, 120, 76, 55));
                    g2.fill(new Ellipse2D.Double(cx - r - 5, cy - r - 5, 2 * r + 10, 2 * r + 10));
                }

                if (done || active) {
                    g2.setColor(active ? DARK_GREEN : LIGHT_GREEN);
                    g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                } else {
                    g2.setColor(SOFT_WHITE);
                    g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                    g2.setColor(BORDER);
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(new Ellipse2D.Double(cx - r + 1, cy - r + 1, 2 * r - 2, 2 * r - 2));
                }

                if (done) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Path2D.Double() {{
                        moveTo(cx - 6, cy);
                        lineTo(cx - 2, cy + 5);
                        lineTo(cx + 6, cy - 5);
                    }});
                } else {
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    g2.setColor(active ? Color.WHITE : HINT);
                    String s = String.valueOf(i + 1);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(s, cx - fm.stringWidth(s) / 2, cy + fm.getAscent() / 2 - 1);
                }

                g2.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 12));
                g2.setColor(active || done ? DARK_GREEN : HINT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(labels[i], cx - fm.stringWidth(labels[i]) / 2, cy + r + 18);
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------------
    //  Password strength meter
    // ---------------------------------------------------------------------

    /** Shows a strength bar and a small checklist under the password box. */
    private static class PasswordHelper extends JComponent {

        private String password = "";

        PasswordHelper() {
            setPreferredSize(new Dimension(100, 44));
        }

        void setPassword(String p) {
            password = p == null ? "" : p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Fx.smooth(g);

            boolean lengthOk = password.length() >= 8;
            boolean numberOk = password.matches(".*\\d.*");
            boolean symbolOk = password.matches(".*[^A-Za-z0-9\\s].*");
            boolean upperOk = password.matches(".*[A-Z].*");

            int rules = (lengthOk ? 1 : 0) + (numberOk ? 1 : 0) + (symbolOk ? 1 : 0);
            int score = rules;
            if (rules == 3 && password.length() >= 12 && upperOk) {
                score = 4;
            }
            if (password.isEmpty()) {
                score = 0;
            }

            Color[] colours = {
                new Color(215, 220, 212), new Color(210, 70, 60),
                new Color(235, 150, 40), new Color(150, 190, 50), new Color(30, 150, 85)
            };
            String[] words = {"", "Weak", "Fair", "Good", "Strong"};

            // four bars
            int barW = (getWidth() - 90) / 4 - 4;
            for (int i = 0; i < 4; i++) {
                g2.setColor(i < score ? colours[score] : colours[0]);
                g2.fill(new RoundRectangle2D.Double(i * (barW + 4), 2, barW, 6, 6, 6));
            }
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(score == 0 ? HINT : colours[score]);
            g2.drawString(score == 0 ? "Strength" : words[score], getWidth() - 70, 10);

            // checklist
            drawRule(g2, 0, 30, lengthOk, "8+ characters");
            drawRule(g2, 120, 30, numberOk, "A number");
            drawRule(g2, 220, 30, symbolOk, "A symbol (@, #, !)");
            g2.dispose();
        }

        private void drawRule(Graphics2D g2, int x, int y, boolean ok, String text) {
            g2.setColor(ok ? OK_GREEN : new Color(200, 205, 198));
            g2.fill(new Ellipse2D.Double(x, y - 10, 13, 13));
            g2.setColor(Color.WHITE);
            if (ok) {
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Path2D.Double() {{
                    moveTo(x + 3, y - 3.5);
                    lineTo(x + 5.5, y - 1);
                    lineTo(x + 10, y - 7);
                }});
            }
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(ok ? OK_GREEN : HINT);
            g2.drawString(text, x + 19, y);
        }
    }

    // ---------------------------------------------------------------------
    //  Role cards
    // ---------------------------------------------------------------------

    /** A clickable picture card for choosing the role. */
    private static class RoleCard extends JComponent {

        static final int ICON_SUNRISE = 0;
        static final int ICON_HEART   = 1;
        static final int ICON_GLOBE   = 2;

        private final String role, title, subtitle;
        private final int icon;
        private boolean selected = false;
        private final float lift = 0f;          // no hover animation
        private float errorFlash = 0f;
        private Consumer<String> onClick;

        RoleCard(String role, String title, String subtitle, int icon) {
            this.role = role;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            setPreferredSize(new Dimension(120, 160));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.accept(RoleCard.this.role);
                    }
                }
            });
        }

        String getRole() { return role; }

        void setOnClick(Consumer<String> c) { onClick = c; }

        void setSelected(boolean s) {
            selected = s;
            errorFlash = 0f;
            repaint();
        }

        void flashError() {
            errorFlash = 1f;     // stays red until the user picks a role
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Fx.smooth(g);
            int w = getWidth();
            int top = 4 - Math.round(4 * lift);
            int h = getHeight() - 8;

            // shadow
            g2.setColor(new Color(0, 0, 0, 14 + (int) (16 * lift)));
            g2.fill(new RoundRectangle2D.Double(3, top + 5, w - 6, h, 22, 22));

            // body
            g2.setColor(selected ? new Color(232, 245, 237) : FIELD_BG);
            g2.fill(new RoundRectangle2D.Double(2, top, w - 4, h, 22, 22));

            Color border = Fx.blend(BORDER, LIGHT_GREEN, Math.max(lift * 0.6f, selected ? 1f : 0f));
            border = Fx.blend(border, ERROR_RED, errorFlash);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(selected ? 2.4f : 1.4f));
            g2.draw(new RoundRectangle2D.Double(2, top, w - 4, h, 22, 22));

            // icon circle
            int cx = w / 2;
            int cy = top + 44;
            g2.setColor(selected ? LIGHT_GREEN : new Color(226, 240, 231));
            g2.fill(new Ellipse2D.Double(cx - 30, cy - 30, 60, 60));
            drawIcon(g2, cx, cy, selected ? Color.WHITE : DARK_GREEN);

            // title
            g2.setFont(new Font("Georgia", Font.BOLD, 16));
            g2.setColor(DARK_GREEN);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title) / 2, top + 98);

            // subtitle (wrapped, centred)
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(new Color(90, 110, 100));
            fm = g2.getFontMetrics();
            int y = top + 118;
            StringBuilder line = new StringBuilder();
            for (String word : subtitle.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(test) > w - 24 && line.length() > 0) {
                    g2.drawString(line.toString(), cx - fm.stringWidth(line.toString()) / 2, y);
                    y += 15;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            g2.drawString(line.toString(), cx - fm.stringWidth(line.toString()) / 2, y);

            // selected tick badge
            if (selected) {
                g2.setColor(OK_GREEN);
                g2.fill(new Ellipse2D.Double(w - 30, top + 8, 20, 20));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Path2D.Double() {{
                    moveTo(w - 25, top + 18);
                    lineTo(w - 21.5, top + 21.5);
                    lineTo(w - 15, top + 14);
                }});
            }
            g2.dispose();
        }

        /** Draws the little picture inside the circle. */
        private void drawIcon(Graphics2D g2, int cx, int cy, Color c) {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (icon == ICON_SUNRISE) {
                // half sun, rays and horizon
                g2.draw(new Arc2D.Double(cx - 12, cy - 6, 24, 24, 0, 180, Arc2D.OPEN));
                g2.draw(new Line2D.Double(cx - 20, cy + 6, cx + 20, cy + 6));
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians(20 + i * 35);
                    g2.draw(new Line2D.Double(
                        cx + Math.cos(a) * 17, cy + 6 - Math.sin(a) * 17,
                        cx + Math.cos(a) * 23, cy + 6 - Math.sin(a) * 23));
                }
                g2.draw(new Line2D.Double(cx - 12, cy + 13, cx + 12, cy + 13));

            } else if (icon == ICON_HEART) {
                // speech bubble with a heart
                g2.draw(new RoundRectangle2D.Double(cx - 18, cy - 17, 36, 27, 12, 12));
                g2.draw(new Path2D.Double() {{
                    moveTo(cx - 6, cy + 10);
                    lineTo(cx - 10, cy + 18);
                    lineTo(cx + 2, cy + 10);
                }});
                Path2D heart = new Path2D.Double();
                heart.moveTo(cx, cy + 2);
                heart.curveTo(cx - 12, cy - 6, cx - 5, cy - 14, cx, cy - 7);
                heart.curveTo(cx + 5, cy - 14, cx + 12, cy - 6, cx, cy + 2);
                g2.fill(heart);

            } else {
                // globe
                g2.draw(new Ellipse2D.Double(cx - 18, cy - 18, 36, 36));
                g2.draw(new Ellipse2D.Double(cx - 8, cy - 18, 16, 36));
                g2.draw(new Line2D.Double(cx - 18, cy, cx + 18, cy));
                g2.draw(new Arc2D.Double(cx - 16, cy - 10, 32, 20, 180, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(cx - 16, cy - 10, 32, 20, 0, 180, Arc2D.OPEN));
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Window background, white card and scroll bar
    // ---------------------------------------------------------------------

    /** Full-window background picture (cover scaled) with a soft green tint. */
    private static class BackgroundPanel extends JPanel {

        private final BufferedImage image;
        private BufferedImage scaled;

        BackgroundPanel() {
            setLayout(null);
            image = Fx.loadImage("Second.png", "Rebirth.jpeg",
                    "Misty Sunrise Journey to MEPCO.png");
        }

        /** Keeps the white card centred, and not too big on huge screens. */
        @Override
        public void doLayout() {
            if (getComponentCount() == 0) {
                return;
            }
            int w = Math.max(900, Math.min(1260, getWidth() - 30));
            int h = Math.max(640, Math.min(800, getHeight() - 20));
            getComponent(0).setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();

            if (image != null && w > 0 && h > 0) {
                if (scaled == null || scaled.getWidth() != w || scaled.getHeight() != h) {
                    scaled = Fx.cover(image, w, h);
                }
                g2.drawImage(scaled, 0, 0, null);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(196, 224, 205),
                        w, h, new Color(236, 244, 222)));
                g2.fillRect(0, 0, w, h);
            }
            g2.setPaint(new GradientPaint(0, 0, new Color(8, 50, 32, 120),
                    0, h, new Color(8, 40, 28, 190)));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }
    }

    /** The big rounded cream card with a soft shadow. */
    private static class ShellPanel extends JPanel {

        ShellPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Fx.smooth(g);
            int w = getWidth();
            int h = getHeight();

            for (int i = 1; i <= SHADOW; i++) {
                g2.setColor(new Color(0, 0, 0, 7));
                g2.fill(new RoundRectangle2D.Double(
                    SHADOW - i, SHADOW - i + 5, w - 2 * (SHADOW - i), h - 2 * (SHADOW - i), 34 + i, 34 + i));
            }
            g2.setColor(CREAM);
            g2.fill(new RoundRectangle2D.Double(SHADOW, SHADOW, w - 2 * SHADOW, h - 2 * SHADOW, 32, 32));
            g2.dispose();
        }
    }

    /** A panel that is always as wide as the scroll pane (so nothing scrolls sideways). */
    private static class TrackWidthPanel extends JPanel implements Scrollable {

        TrackWidthPanel(LayoutManager lm) {
            super(lm);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 80; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Slim rounded scroll bar. */
    private static class ModernScrollBarUI extends BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(170, 195, 180);
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return zeroButton(); }

        @Override
        protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = Fx.smooth(g);
            g2.setColor(thumbColor);
            g2.fill(new RoundRectangle2D.Double(r.x + 1, r.y, r.width - 2, r.height, 8, 8));
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
    }

    // ---------------------------------------------------------------------
    //  Hero panel : the picture on the left side
    // ---------------------------------------------------------------------

    /**
     * Left side of the card: a still picture + dark green tint + text + progress bar.
     * No animation, so the code is short and easy to explain.
     */
    private static class HeroPanel extends JPanel {

        private final BufferedImage image;     // your picture (Second.png)
        private BufferedImage scaled;          // the picture resized to fit this panel
        private double progress = 0;           // 0.0 to 1.0

        HeroPanel() {
            setOpaque(false);
            image = Fx.loadImage("Second.png", "Rebirth.jpeg",
                    "Misty Sunrise Journey to MEPCO.png");
        }

        /** Called by the form every time the user fills a field. */
        void setTarget(double value) {
            progress = Math.max(0, Math.min(1, value));
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            // 40% of the card, but never smaller than 380 or bigger than 520
            int parentW = getParent() == null ? 1100 : getParent().getWidth();
            int width = Math.max(380, Math.min(520, (int) (parentW * 0.40)));
            return new Dimension(width, 600);
        }

        @Override
        protected void paintComponent(Graphics g) {

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = Fx.smooth(g);

            // 1. keep only the rounded shape (left corners rounded)
            g2.clip(new RoundRectangle2D.Double(0, 0, w + 40, h, 32, 32));

            // 2. the picture (or a green gradient if the picture is missing)
            if (image != null) {
                if (scaled == null || scaled.getWidth() != w + 40 || scaled.getHeight() != h) {
                    scaled = Fx.cover(image, w + 40, h);
                }
                g2.drawImage(scaled, 0, 0, null);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(40, 125, 88),
                        0, h, new Color(10, 55, 38)));
                g2.fillRect(0, 0, w + 40, h);
            }

            // 3. dark green tint so the white text is easy to read
            g2.setPaint(new GradientPaint(0, 0, new Color(8, 55, 36, 140),
                    0, h, new Color(5, 35, 25, 220)));
            g2.fillRect(0, 0, w + 40, h);

            // 4. texts
            int x = 36;
            g2.setFont(new Font("Georgia", Font.BOLD, 40));
            g2.setColor(Color.WHITE);
            g2.drawString("REbirth", x, 66);

            g2.setFont(new Font("Georgia", Font.ITALIC, 16));
            g2.setColor(new Color(225, 240, 228));
            g2.drawString("A new beginning starts with support.", x, 94);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.setColor(new Color(205, 225, 212));
            Fx.drawWrapped(g2, "Schemes, jobs and counselling - everything you need "
                    + "to rebuild your life, in one place.", x, 128, w - 72, 20);

            // (live progress bar removed - the picture is now completely still)

            g2.dispose();
        }
    }
}
