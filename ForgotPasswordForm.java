package rebirth;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * REbirth - Forgot Password
 *
 * Same look as the Login screen (green theme, cream card, same background).
 * No animation. Messages appear inside the card instead of pop-up boxes.
 *
 * Steps for the user:
 *   1. Type the username and press "Find Question"
 *   2. Answer the security question
 *   3. Type the new password twice and press "Reset Password"
 *
 * Uses LoginForm.BackgroundPanel, LoginForm.GlassCard, LoginForm.RoundedButton
 * and LoginForm.RoundedBorder, so those classes must stay in LoginForm.java.
 */
public class ForgotPasswordForm extends JFrame {

    // ---------- Colours (same as LoginForm) ----------
    private static final Color DARK_GREEN  = new Color(13, 75, 49);
    private static final Color LIGHT_GREEN = new Color(24, 120, 76);
    private static final Color BORDER      = new Color(190, 196, 184);
    private static final Color HINT        = new Color(140, 148, 140);
    private static final Color SOFT_WHITE  = new Color(255, 255, 250);
    private static final Color ERROR_RED   = new Color(178, 34, 34);
    private static final Color OK_GREEN    = new Color(34, 139, 84);

    private static final int SHADOW = 14;

    // Same rule as the Registration form
    private static final String PASSWORD_REGEX =
        "^(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$";

    // ---------- Components ----------
    private JTextField     usernameField;
    private JTextField     answerField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private JCheckBox      showPassword;
    private JLabel         questionLabel;
    private JLabel         messageLabel;

    private LoginForm.RoundedButton findButton;
    private LoginForm.RoundedButton resetButton;

    private boolean questionFound = false;   // true after "Find Question" succeeds
    private boolean busy = false;

    public ForgotPasswordForm() {

        setTitle("REbirth - Forgot Password");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 720));

        LoginForm.BackgroundPanel background = new LoginForm.BackgroundPanel();
        background.setLayout(new GridBagLayout());
        background.add(createCard());
        setContentPane(background);

        setLocationRelativeTo(null);
    }

    // =====================================================
    //  CARD
    // =====================================================
    private JPanel createCard() {

        LoginForm.GlassCard card = new LoginForm.GlassCard(32);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(SHADOW + 24, SHADOW + 40, SHADOW + 24, SHADOW + 40));
        card.setPreferredSize(new Dimension(590, 740));

        int row = 0;

        JLabel heading = new JLabel("Forgot Password", SwingConstants.CENTER);
        heading.setFont(new Font("Georgia", Font.BOLD, 32));
        heading.setForeground(DARK_GREEN);
        addRow(card, heading, row++, 0);

        JLabel subtitle = new JLabel(
            "Answer your security question to set a new password.",
            SwingConstants.CENTER);
        subtitle.setFont(new Font("Georgia", Font.ITALIC, 14));
        subtitle.setForeground(DARK_GREEN);
        addRow(card, subtitle, row++, 6);

        JSeparator divider = new JSeparator();
        divider.setForeground(BORDER);
        addRow(card, divider, row++, 14);

        // ---- Step 1 : username ----
        addRow(card, createLabel("Username"), row++, 16);

        usernameField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintRound(g, this);
                super.paintComponent(g);
                paintHint(g, this, "Enter your username");
            }
        };
        styleField(usernameField);
        addRow(card, usernameField, row++, 6);

        findButton = new LoginForm.RoundedButton(
            "Find Question", SOFT_WHITE, DARK_GREEN, BORDER, false);
        findButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        findButton.setPreferredSize(new Dimension(0, 44));
        findButton.addActionListener(e -> findSecurityQuestion());
        addRow(card, findButton, row++, 10);

        // ---- Step 2 : question + answer ----
        addRow(card, createLabel("Security Question"), row++, 16);

        questionLabel = new JLabel(
            "<html>Enter your username and click Find Question.</html>");
        questionLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        questionLabel.setForeground(new Color(70, 85, 75));
        questionLabel.setPreferredSize(new Dimension(0, 40));
        addRow(card, questionLabel, row++, 4);

        addRow(card, createLabel("Security Answer"), row++, 8);

        answerField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintRound(g, this);
                super.paintComponent(g);
                paintHint(g, this, "Type your answer");
            }
        };
        styleField(answerField);
        addRow(card, answerField, row++, 6);

        // ---- Step 3 : new password ----
        addRow(card, createLabel("New Password"), row++, 14);

        newPasswordField = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintRound(g, this);
                super.paintComponent(g);
                paintHint(g, this, "8+ characters, a number and a symbol");
            }
        };
        styleField(newPasswordField);
        addRow(card, newPasswordField, row++, 6);

        addRow(card, createLabel("Confirm Password"), row++, 14);

        confirmPasswordField = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintRound(g, this);
                super.paintComponent(g);
                paintHint(g, this, "Type the new password again");
            }
        };
        styleField(confirmPasswordField);
        addRow(card, confirmPasswordField, row++, 6);

        showPassword = new JCheckBox("Show password");
        showPassword.setOpaque(false);
        showPassword.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        showPassword.setForeground(DARK_GREEN);
        showPassword.setFocusPainted(false);
        showPassword.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showPassword.addActionListener(e -> {
            char echo = showPassword.isSelected() ? (char) 0 : '\u2022';
            newPasswordField.setEchoChar(echo);
            confirmPasswordField.setEchoChar(echo);
        });
        addRow(card, showPassword, row++, 6);

        // ---- message (red = error, green = success) ----
        messageLabel = new JLabel(" ", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        messageLabel.setForeground(ERROR_RED);
        addRow(card, messageLabel, row++, 8);

        // ---- buttons ----
        resetButton = new LoginForm.RoundedButton(
            "RESET PASSWORD", LIGHT_GREEN, Color.WHITE, DARK_GREEN, true);
        resetButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        resetButton.setPreferredSize(new Dimension(0, 50));
        resetButton.addActionListener(e -> resetPassword());
        addRow(card, resetButton, row++, 4);

        LoginForm.RoundedButton clearButton = new LoginForm.RoundedButton(
            "Clear", SOFT_WHITE, DARK_GREEN, BORDER, false);
        clearButton.addActionListener(e -> clearFields());

        LoginForm.RoundedButton backButton = new LoginForm.RoundedButton(
            "Back to Login", SOFT_WHITE, DARK_GREEN, BORDER, false);
        backButton.addActionListener(e -> {
            dispose();
            new LoginForm();            // LoginForm shows itself
        });

        JPanel bottom = new JPanel(new GridLayout(1, 2, 14, 0));
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(0, 44));
        bottom.add(clearButton);
        bottom.add(backButton);
        addRow(card, bottom, row++, 12);

        // Enter key support
        usernameField.addActionListener(e -> findSecurityQuestion());
        answerField.addActionListener(e -> newPasswordField.requestFocusInWindow());
        newPasswordField.addActionListener(e -> confirmPasswordField.requestFocusInWindow());
        confirmPasswordField.addActionListener(e -> resetPassword());

        return card;
    }

    // =====================================================
    //  SMALL LAYOUT / STYLE HELPERS
    // =====================================================
    private void addRow(JPanel panel, Component comp, int row, int topGap) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(topGap, 0, 0, 0);
        panel.add(comp, gbc);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(DARK_GREEN);
        return label;
    }

    private void styleField(JTextField field) {
        field.setOpaque(false);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        field.setForeground(new Color(45, 65, 53));
        field.setCaretColor(DARK_GREEN);
        field.setBorder(new LoginForm.RoundedBorder(BORDER, 18, new Insets(0, 16, 0, 16)));
        field.setPreferredSize(new Dimension(0, 44));
    }

    // White rounded background behind a text field
    private static void paintRound(Graphics g, JComponent f) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(f.isEnabled() ? Color.WHITE : new Color(238, 240, 235));
        g2.fill(new RoundRectangle2D.Float(0, 0, f.getWidth() - 1, f.getHeight() - 1, 18, 18));
        g2.dispose();
    }

    // Grey placeholder text when the field is empty
    private static void paintHint(Graphics g, JTextField f, String hint) {
        boolean empty = (f instanceof JPasswordField)
                ? ((JPasswordField) f).getPassword().length == 0
                : f.getText().isEmpty();
        if (!empty) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(HINT);
        g2.setFont(f.getFont());
        Insets in = f.getInsets();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, in.left, (f.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private void showError(String text) {
        messageLabel.setForeground(ERROR_RED);
        messageLabel.setText(text);
    }

    private void showSuccess(String text) {
        messageLabel.setForeground(OK_GREEN);
        messageLabel.setText(text);
    }

    private void setBusy(boolean value) {
        busy = value;
        findButton.setEnabled(!value);
        resetButton.setEnabled(!value);
        setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
                        : Cursor.getDefaultCursor());
    }

    // =====================================================
    //  STEP 1 : FIND THE SECURITY QUESTION
    // =====================================================
    private void findSecurityQuestion() {

        if (busy) {
            return;
        }

        final String username = usernameField.getText().trim();
        showError(" ");

        if (username.isEmpty()) {
            showError("Please enter your username.");
            usernameField.requestFocusInWindow();
            return;
        }

        setBusy(true);

        // Database work runs in the background so the window never freezes
        new SwingWorker<String[], Void>() {

            @Override
            protected String[] doInBackground() throws Exception {
                String sql = "SELECT SECURITY_QUESTION FROM USERS WHERE USERNAME = ?";

                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, username);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;                       // username not found
                        }
                        return new String[]{rs.getString("SECURITY_QUESTION")};
                    }
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    String[] result = get();

                    if (result == null) {
                        questionFound = false;
                        questionLabel.setText("<html>Username not found.</html>");
                        showError("Username not found.");
                        usernameField.requestFocusInWindow();
                        usernameField.selectAll();

                    } else if (result[0] == null || result[0].trim().isEmpty()) {
                        questionFound = false;
                        questionLabel.setText(
                            "<html>No security question is registered.</html>");
                        showError("This account has no security question.");

                    } else {
                        questionFound = true;
                        questionLabel.setText("<html><b>"
                            + escapeHtml(result[0]) + "</b></html>");
                        showSuccess("Question found. Please answer it below.");
                        answerField.requestFocusInWindow();
                    }

                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showError("Database error: " + cause.getMessage());
                }
            }
        }.execute();
    }

    // =====================================================
    //  STEP 2 + 3 : CHECK THE ANSWER AND SAVE THE NEW PASSWORD
    // =====================================================
    private void resetPassword() {

        if (busy) {
            return;
        }

        final String username = usernameField.getText().trim();
        final String answer = answerField.getText().trim();
        final String newPassword = new String(newPasswordField.getPassword());
        final String confirm = new String(confirmPasswordField.getPassword());

        if (username.isEmpty() || answer.isEmpty()
                || newPassword.isEmpty() || confirm.isEmpty()) {
            showError("Please fill in all the fields.");
            return;
        }
        if (!questionFound) {
            showError("Click \"Find Question\" first.");
            return;
        }
        if (!newPassword.matches(PASSWORD_REGEX)) {
            showError("Password needs 8+ characters, a number and a symbol.");
            return;
        }
        if (!newPassword.equals(confirm)) {
            showError("The two passwords do not match.");
            confirmPasswordField.setText("");
            confirmPasswordField.requestFocusInWindow();
            return;
        }

        showError(" ");
        setBusy(true);

        new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() throws Exception {

                String checkSql =
                    "SELECT USER_ID FROM USERS WHERE USERNAME = ? "
                    + "AND LOWER(TRIM(SECURITY_ANSWER)) = LOWER(?)";

                String updateSql =
                    "UPDATE USERS SET PASSWORD = ? WHERE USERNAME = ?";

                try (Connection con = DBConnection.getConnection()) {

                    con.setAutoCommit(false);

                    try {
                        // 1. is the answer correct?
                        try (PreparedStatement check = con.prepareStatement(checkSql)) {
                            check.setString(1, username);
                            check.setString(2, answer);

                            try (ResultSet rs = check.executeQuery()) {
                                if (!rs.next()) {
                                    con.rollback();
                                    return false;          // wrong answer
                                }
                            }
                        }

                        // 2. save the new password (always hashed, never plain text)
                        String hashed = PasswordUtil.hashPassword(newPassword);

                        try (PreparedStatement update = con.prepareStatement(updateSql)) {
                            update.setString(1, hashed);
                            update.setString(2, username);

                            if (update.executeUpdate() != 1) {
                                con.rollback();
                                throw new Exception("Password could not be updated.");
                            }
                        }

                        con.commit();
                        return true;

                    } catch (Exception ex) {
                        con.rollback();
                        throw ex;
                    }
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(ForgotPasswordForm.this,
                            "Password reset successfully!\n"
                            + "Please log in with your new password.");
                        dispose();
                        new LoginForm();               // LoginForm shows itself
                    } else {
                        showError("Incorrect username or security answer.");
                        answerField.requestFocusInWindow();
                        answerField.selectAll();
                    }

                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showError("Error: " + cause.getMessage());
                }
            }
        }.execute();
    }

    // =====================================================
    //  CLEAR + HTML SAFETY
    // =====================================================
    private void clearFields() {
        usernameField.setText("");
        answerField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
        showPassword.setSelected(false);
        newPasswordField.setEchoChar('\u2022');
        confirmPasswordField.setEchoChar('\u2022');
        questionFound = false;
        questionLabel.setText("<html>Enter your username and click Find Question.</html>");
        showError(" ");
        usernameField.requestFocusInWindow();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ForgotPasswordForm().setVisible(true));
    }
}