package rebirth;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginForm extends JFrame {

    // ---------- Colors ----------
    private static final Color DARK_GREEN  = new Color(13, 75, 49);
    private static final Color LIGHT_GREEN = new Color(24, 120, 76);
    private static final Color GLASS_CREAM = new Color(250, 248, 238, 238); // slightly see-through
    private static final Color BORDER      = new Color(190, 196, 184);
    private static final Color HINT        = new Color(140, 148, 140);
    private static final Color SOFT_WHITE  = new Color(255, 255, 250);
    private static final Color ERROR_RED   = new Color(178, 34, 34);

    private static final int SHADOW = 14;   // space around the card used for the shadow

    // ---------- Components ----------
    private JTextField     usernameField;
    private JPasswordField passwordField;
    private RoundedButton  loginButton;
    private JLabel         errorLabel;

    // ---------- Needed for the shake animation ----------
    private BackgroundPanel    background;
    private GridBagLayout      backgroundLayout;
    private GridBagConstraints cardConstraints;
    private JPanel             cardPanel;

    private boolean busy = false;   // true while the database check is running

    public LoginForm() {
        setTitle("REBirth - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 720));
        setLocationRelativeTo(null);

        background = new BackgroundPanel();
        backgroundLayout = new GridBagLayout();
        background.setLayout(backgroundLayout);

        cardPanel = createLoginCard();

        cardConstraints = new GridBagConstraints();
        cardConstraints.weightx = 1;
        cardConstraints.weighty = 1;
        cardConstraints.anchor = GridBagConstraints.EAST;
        cardConstraints.insets = new Insets(20, 20, 20, 60);
        background.add(cardPanel, cardConstraints);

        setContentPane(background);
        setVisible(true);
    }

    // =====================================================
    //  LOGIN CARD
    // =====================================================
    private JPanel createLoginCard() {
        GlassCard card = new GlassCard(32);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(SHADOW + 26, SHADOW + 40, SHADOW + 26, SHADOW + 40));
        card.setPreferredSize(new Dimension(570, 670));

        int row = 0;

        // Heading
        JLabel heading = new JLabel("Login", SwingConstants.CENTER);
        heading.setFont(new Font("Georgia", Font.BOLD, 34));
        heading.setForeground(DARK_GREEN);
        addRow(card, heading, row++, 0);

        // Subtitle
        JLabel subtitle = new JLabel(
                "Every step forward brings a brighter tomorrow.", SwingConstants.CENTER);
        subtitle.setFont(new Font("Georgia", Font.ITALIC, 15));
        subtitle.setForeground(DARK_GREEN);
        addRow(card, subtitle, row++, 6);

        // Divider
        JSeparator divider = new JSeparator();
        divider.setForeground(BORDER);
        addRow(card, divider, row++, 16);

        // Username
        addRow(card, createLabel("Username"), row++, 22);
        usernameField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintFieldBackground(g, this, true);
                super.paintComponent(g);
                paintHint(g, this, "Enter your username");
            }
        };
        styleField(usernameField);
        addRow(card, usernameField, row++, 8);

        // Password (no show/hide button)
        addRow(card, createLabel("Password"), row++, 18);
        passwordField = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                paintFieldBackground(g, this, false);
                super.paintComponent(g);
                paintHint(g, this, "Enter your password");
            }
        };
        styleField(passwordField);
        addRow(card, passwordField, row++, 8);

        // Error message (replaces popup dialogs for wrong login)
        errorLabel = new JLabel(" ", SwingConstants.CENTER);
        errorLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        errorLabel.setForeground(ERROR_RED);
        addRow(card, errorLabel, row++, 10);

        // Login button (gradient)
        loginButton = new RoundedButton("LOGIN", LIGHT_GREEN, Color.WHITE, DARK_GREEN, true);
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginButton.setPreferredSize(new Dimension(0, 52));
        loginButton.addActionListener(e -> loginUser());
        addRow(card, loginButton, row++, 6);

        // Enter key support
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> loginUser());

        // OR divider
        JPanel orPanel = new JPanel(new GridBagLayout());
        orPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        orPanel.add(new JSeparator(), c);
        c.weightx = 0;
        JLabel or = new JLabel("   OR   ");
        or.setFont(new Font("Segoe UI", Font.BOLD, 14));
        or.setForeground(DARK_GREEN);
        orPanel.add(or, c);
        c.weightx = 1;
        orPanel.add(new JSeparator(), c);
        addRow(card, orPanel, row++, 16);

        // Create Account + Forgot Password (equal width)
        RoundedButton createBtn =
                new RoundedButton("Create Account", SOFT_WHITE, DARK_GREEN, BORDER, false);
        createBtn.addActionListener(e -> {
            dispose();
            new RegistrationForm();
        });

        RoundedButton forgotBtn =
                new RoundedButton("Forgot Password?", SOFT_WHITE, DARK_GREEN, BORDER, false);
        forgotBtn.addActionListener(e -> {
            dispose();
            new ForgotPasswordForm().setVisible(true);
        });

        JPanel bottom = new JPanel(new GridLayout(1, 2, 14, 0));
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(0, 48));
        bottom.add(createBtn);
        bottom.add(forgotBtn);
        addRow(card, bottom, row++, 16);

        return card;
    }

    /** Adds one full-width row to the card. */
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
        label.setFont(new Font("Segoe UI", Font.BOLD, 17));
        label.setForeground(DARK_GREEN);
        return label;
    }

    private void styleField(JTextField field) {
        field.setOpaque(false);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        field.setForeground(new Color(45, 65, 53));
        field.setCaretColor(DARK_GREEN);
        // left inset 46 leaves room for the icon
        field.setBorder(new RoundedBorder(BORDER, 18, new Insets(0, 46, 0, 16)));
        field.setPreferredSize(new Dimension(0, 50));

        // Green glow while the field is focused
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.putClientProperty("focused", Boolean.TRUE);
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.putClientProperty("focused", Boolean.FALSE);
                field.repaint();
            }
        });
    }

    // White rounded background + glow + icon
    private static void paintFieldBackground(Graphics g, JTextField f, boolean userIcon) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = f.getWidth();
        int h = f.getHeight();

        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 18, 18));

        // glow
        if (Boolean.TRUE.equals(f.getClientProperty("focused"))) {
            g2.setColor(new Color(24, 120, 76, 55));
            g2.setStroke(new BasicStroke(5f));
            g2.draw(new RoundRectangle2D.Float(2, 2, w - 5, h - 5, 16, 16));
        }

        // icon
        g2.setColor(DARK_GREEN);
        int x = 16;
        int cy = h / 2;
        if (userIcon) {
            g2.fillOval(x + 5, cy - 9, 8, 8);
            g2.fillArc(x + 1, cy + 1, 16, 14, 0, 180);
        } else {
            g2.fillRoundRect(x + 2, cy - 1, 14, 10, 3, 3);
            g2.setStroke(new BasicStroke(2f));
            g2.drawArc(x + 4, cy - 9, 10, 12, 0, 180);
            g2.drawLine(x + 4, cy - 3, x + 4, cy - 1);
            g2.drawLine(x + 14, cy - 3, x + 14, cy - 1);
        }
        g2.dispose();
    }

    // Grey placeholder text when the field is empty
    private static void paintHint(Graphics g, JTextField f, String hint) {
        boolean empty = (f instanceof JPasswordField)
                ? ((JPasswordField) f).getPassword().length == 0
                : f.getText().isEmpty();
        if (!empty) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(HINT);
        g2.setFont(f.getFont());
        Insets in = f.getInsets();
        FontMetrics fm = g2.getFontMetrics();
        int y = (f.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(hint, in.left, y);
        g2.dispose();
    }

    // =====================================================
    //  MESSAGE + SHAKE
    // =====================================================
    private void showError(String message) {
        errorLabel.setText(message);
        shakeCard();
    }

    /** Quick left-right shake of the login card. */
    private void shakeCard() {
        final int[] offsets = {-10, 10, -8, 8, -5, 5, -2, 2, 0};
        final int[] index = {0};

        Timer timer = new Timer(25, null);
        timer.addActionListener(e -> {
            cardConstraints.insets = new Insets(20, 20, 20, 60 + offsets[index[0]]);
            backgroundLayout.setConstraints(cardPanel, cardConstraints);
            background.revalidate();
            background.repaint();

            if (++index[0] >= offsets.length) {
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    // =====================================================
    //  LOGIN LOGIC (Oracle, runs in background thread)
    // =====================================================
    private static class LoginResult {
        static final int NO_USER = 0, BAD_PASSWORD = 1, OK = 2;
        int status;
        int userId;
        String fullName;
        String role;
    }

    private void loginUser() {
        if (busy) return;

        final String username = usernameField.getText().trim();
        final String password = new String(passwordField.getPassword());

        errorLabel.setText(" ");

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        busy = true;
        loginButton.setText("Signing in...");

        new SwingWorker<LoginResult, Void>() {

            @Override
            protected LoginResult doInBackground() throws Exception {
                String sql = "SELECT user_id, full_name, password, role "
                           + "FROM USERS WHERE username = ?";

                LoginResult result = new LoginResult();

                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, username);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            result.status = LoginResult.NO_USER;
                            return result;
                        }

                        String savedPassword = rs.getString("password");

                        if (PasswordUtil.verifyPassword(password, savedPassword)) {
                            result.status   = LoginResult.OK;
                            result.userId   = rs.getInt("user_id");
                            result.fullName = rs.getString("full_name");
                            result.role     = rs.getString("role");
                        } else {
                            result.status = LoginResult.BAD_PASSWORD;
                        }
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                busy = false;
                loginButton.setText("LOGIN");

                try {
                    LoginResult r = get();

                    switch (r.status) {
                        case LoginResult.OK:
                            new RoleBasedDashboard(r.userId, r.fullName, r.role)
                                    .setVisible(true);
                            LoginForm.this.dispose();
                            break;

                        case LoginResult.BAD_PASSWORD:
                            showError("Incorrect password. Please try again.");
                            passwordField.setText("");
                            passwordField.requestFocusInWindow();
                            break;

                        default:
                            showError("Username not found.");
                            usernameField.requestFocusInWindow();
                            usernameField.selectAll();
                            passwordField.setText("");
                            break;
                    }

                } catch (Exception ex) {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LoginForm.this,
                            "Unable to login: " + cause.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                    cause.printStackTrace();
                }
            }
        }.execute();
    }

    // =====================================================
    //  SMALL HELPER CLASSES
    // =====================================================

    /** Background image, scaled to cover the window without stretching. */
    static class BackgroundPanel extends JPanel {
        private BufferedImage image;

        BackgroundPanel() {
            try {
                File file = new File("src/rebirth/Rebirth.jpeg");
                if (file.exists()) {
                    image = ImageIO.read(file);
                } else {
                    URL url = LoginForm.class.getResource("/rebirth/Rebirth.jpeg");
                    if (url != null) image = ImageIO.read(url);
                }
            } catch (Exception e) {
                System.out.println("Background image could not be loaded: " + e.getMessage());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            if (image == null) {
                g2.setColor(new Color(225, 237, 218));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.max((double) getWidth() / image.getWidth(),
                                        (double) getHeight() / image.getHeight());
                int w = (int) (image.getWidth() * scale);
                int h = (int) (image.getHeight() * scale);
                g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, this);
            }
            g2.dispose();
        }
    }

    /** Frosted-glass card: translucent cream fill + soft shadow around it. */
    static class GlassCard extends JPanel {
        private final int radius;

        GlassCard(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // soft shadow: many faint layers growing outward
            for (int i = 1; i <= SHADOW; i++) {
                g2.setColor(new Color(0, 0, 0, 5));
                g2.fill(new RoundRectangle2D.Float(
                        SHADOW - i, SHADOW - i + 3,
                        w - 2 * SHADOW + 2 * i, h - 2 * SHADOW + 2 * i,
                        radius + 2 * i, radius + 2 * i));
            }

            // card body
            g2.setColor(GLASS_CREAM);
            g2.fill(new RoundRectangle2D.Float(
                    SHADOW, SHADOW, w - 2 * SHADOW, h - 2 * SHADOW, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Rounded button. gradient = true gives the green gradient + hover glow. */
    static class RoundedButton extends JButton {
        private final Color fill, outline;
        private final boolean gradient;

        RoundedButton(String text, Color fill, Color textColor, Color outline, boolean gradient) {
            super(text);
            this.fill = fill;
            this.outline = outline;
            this.gradient = gradient;
            setForeground(textColor);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean hover = getModel().isRollover();
            RoundRectangle2D shape = new RoundRectangle2D.Float(
                    1, 1, getWidth() - 3, getHeight() - 3, 18, 18);

            if (gradient) {
                Color top = hover ? fill.brighter() : fill;
                Color bottom = hover ? fill : fill.darker();
                g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            } else {
                g2.setColor(hover ? new Color(235, 243, 232) : fill);
            }
            g2.fill(shape);

            g2.setPaint(outline);
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    /** Thin rounded outline for text fields (turns green when focused). */
    static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final Insets insets;

        RoundedBorder(Color color, int radius, Insets insets) {
            this.color = color;
            this.radius = radius;
            this.insets = insets;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(insets.top, insets.left, insets.bottom, insets.right);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            boolean focused = (c instanceof JComponent)
                    && Boolean.TRUE.equals(((JComponent) c).getClientProperty("focused"));

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(focused ? LIGHT_GREEN : color);
            g2.setStroke(new BasicStroke(focused ? 2f : 1.3f));
            g2.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 3, h - 3, radius, radius));
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginForm::new);
    }
}
