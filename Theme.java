package rebirth;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import javax.swing.*;

/**
 * Shared look-and-feel helpers for the REbirth screens.
 * GovernmentSchemesForm depends on this class.
 */
public final class Theme {

    private Theme() { }

    /** Touching this class installs the shared look (scroll bars, combo boxes). */
    public static void install() { }

    // ---------------------------------------------------------------
    // Colours
    // ---------------------------------------------------------------
    public static final Color PAGE_BG    = new Color(233, 245, 238);
    public static final Color GREEN      = new Color(34, 120, 84);
    public static final Color GREEN_DARK = new Color(22, 90, 62);
    public static final Color GREEN_SOFT = new Color(222, 240, 230);
    public static final Color TEXT       = new Color(32, 45, 40);
    public static final Color MUTED      = new Color(105, 120, 112);
    public static final Color BORDER     = new Color(205, 218, 210);
    /** Clearly visible outline for text boxes, drop-downs and notes areas. */
    public static final Color INPUT_BORDER = new Color(112, 160, 134);
    public static final Color RED        = new Color(190, 50, 50);
    public static final Color ORANGE     = new Color(205, 105, 20);

    private static final Color[] ACCENTS = {
        new Color(34, 120, 84),   // green
        new Color(43, 108, 176),  // blue
        new Color(196, 98, 45),   // orange
        new Color(124, 77, 160),  // purple
        new Color(190, 60, 80),   // red
        new Color(0, 130, 140),   // teal
        new Color(160, 120, 20)   // gold
    };

    // ---------------------------------------------------------------
    // Small static helpers
    // ---------------------------------------------------------------
    public static Font f(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    /** null -> "" */
    public static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Escapes text so it is safe inside Swing HTML labels. */
    public static String esc(String s) {
        return nz(s).replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    /** Cuts long text and adds "..." */
    public static String shorten(String s, int max) {
        String t = nz(s).replaceAll("\\s+", " ").trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    /** Same type text always gets the same colour. */
    public static Color accentFor(String type) {
        String t = nz(type).trim().toLowerCase();
        if (t.isEmpty()) {
            return ACCENTS[0];
        }
        return ACCENTS[Math.abs(t.hashCode()) % ACCENTS.length];
    }

    public static void quality(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
    }

    /** Wrapping text label (HTML) with a fixed width. */
    public static JLabel text(String s, int width, int size, Color color) {
        String html = esc(s).replace("\r\n", "\n").replace("\n", "<br>");
        JLabel l = new JLabel("<html><body style='width:" + width + "px'>"
            + html + "</body></html>");
        l.setFont(f(Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static JTextField field() {
        JTextField t = new JTextField() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                quality(g);
                int w = getWidth();
                int h = getHeight();
                boolean focus = hasFocus();
                if (focus) {
                    g.setColor(new Color(34, 120, 84, 55));
                    g.fill(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
                }
                g.setColor(new Color(255, 255, 255));
                g.fill(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 12, 12));
                g.setColor(new Color(16, 70, 45, 22));
                g.fill(new RoundRectangle2D.Double(2.5, 2.5, w - 5, 5, 6, 6));
                g.setColor(focus ? GREEN : INPUT_BORDER);
                g.setStroke(new BasicStroke(focus ? 2.2f : 1.5f));
                g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 12, 12));
                g.dispose();
                super.paintComponent(g0);
            }
        };
        t.setOpaque(false);
        t.setFont(f(Font.PLAIN, 14));
        t.setForeground(TEXT);
        t.setPreferredSize(new Dimension(200, 38));
        t.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        t.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                t.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                t.repaint();
            }
        });
        return t;
    }

    public static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 30);
        a.setFont(f(Font.PLAIN, 14));
        a.setForeground(TEXT);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return a;
    }

    public static JScrollPane scroll(JTextArea a) {
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(INPUT_BORDER, 2));
        slim(sp);
        sp.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    public static void openUrl(Component parent, String url) {
        String u = nz(url).trim();
        if (u.isEmpty()) {
            return;
        }
        if (!u.matches("(?i)^https?://.*")) {
            u = "https://" + u;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(u));
            } else {
                JOptionPane.showMessageDialog(parent, "Open this link:\n" + u);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                "Could not open the website:\n" + u);
        }
    }

    // ---------------------------------------------------------------
    // Card: rounded white panel with soft shadow
    // ---------------------------------------------------------------
    public static class Card extends JPanel {

        private final Color fill;
        private boolean highlight;
        private boolean hover;

        public Card(Color fill) {
            this.fill = fill;
            setOpaque(false);
        }

        public void setHighlight(boolean b) {
            highlight = b;
            repaint();
        }

        public void setHover(boolean b) {
            hover = b;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);

            int w = getWidth();
            int h = getHeight();

            // layered soft shadow
            for (int i = 0; i < 5; i++) {
                g2.setColor(new Color(16, 70, 45, (hover ? 14 : 9)));
                g2.fill(new RoundRectangle2D.Double(
                    2 - i, 4 - i, w - 4 + 2 * i, h - 8 + 2 * i, 22 + i * 2, 22 + i * 2));
            }

            // glass body: bright top, soft bottom
            Color bottom = fill.equals(Color.WHITE) ? new Color(244, 250, 246) : fill;
            g2.setPaint(new GradientPaint(0, 0, fill, 0, h, bottom));
            g2.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 6, 20, 20));

            // top sheen
            g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 120),
                0, 26, new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Double(1, 1, w - 3, 26, 20, 20));

            // outline
            if (highlight) {
                g2.setColor(GREEN);
                g2.setStroke(new BasicStroke(2.2f));
            } else {
                g2.setColor(hover ? new Color(120, 190, 150) : new Color(214, 228, 219));
                g2.setStroke(new BasicStroke(1f));
            }
            g2.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 6, 20, 20));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Badge: small rounded label
    // ---------------------------------------------------------------
    public static class Badge extends JLabel {

        private final Color bg;

        public Badge(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(f(Font.BOLD, 11));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(
                0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Button: flat rounded button (background, text colour)
    // ---------------------------------------------------------------
    public static class Button extends JButton {

        private final Color bg;

        public Button(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(f(Font.BOLD, 13));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = getWidth();
            int h = getHeight();
            boolean light = bg.getAlpha() < 255 || bg.getRed() + bg.getGreen()
                + bg.getBlue() > 700;
            Color top = bg;
            if (getModel().isPressed()) {
                top = bg.darker();
            } else if (getModel().isRollover()) {
                top = bg.brighter();
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 60));
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 18, 18));
            }
            g2.setPaint(new GradientPaint(0, 2, top, 0, h - 2,
                light ? top : top.darker()));
            g2.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 3, 16, 16));
            if (!light) {
                g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 70),
                    0, h / 2f, new Color(255, 255, 255, 0)));
                g2.fill(new RoundRectangle2D.Double(1, 1, w - 2, h / 2.0, 16, 16));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Chip: toggle button used for the type filter
    // ---------------------------------------------------------------
    public static class Chip extends JToggleButton {

        public Chip(String text) {
            super(text);
            setFont(f(Font.BOLD, 12));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = getWidth();
            int h = getHeight();
            if (isSelected()) {
                g2.setColor(GREEN);
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
                setForeground(Color.WHITE);
            } else {
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h, h));
                g2.setColor(BORDER);
                g2.draw(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h, h));
                setForeground(TEXT);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // HeaderBand: green banner with title, subtitle and right-side items
    // ---------------------------------------------------------------
    public static class HeaderBand extends JPanel {

        private final JPanel right =
            new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        public HeaderBand(String title, String subtitle) {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(16, 26, 18, 26));

            // breadcrumb:  Dashboard > Page name
            JLabel crumb = new JLabel("Dashboard  \u203A  " + title);
            crumb.setFont(f(Font.PLAIN, 12));
            crumb.setForeground(new Color(190, 228, 208));

            JLabel t = new JLabel(title);
            t.setFont(f(Font.BOLD, 26));
            t.setForeground(Color.WHITE);

            JLabel s = new JLabel(subtitle);
            s.setFont(f(Font.PLAIN, 14));
            s.setForeground(new Color(215, 238, 225));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.add(crumb);
            left.add(Box.createVerticalStrut(4));
            left.add(t);
            left.add(Box.createVerticalStrut(4));
            left.add(s);

            right.setOpaque(false);

            // back button: closes this screen and returns to the dashboard
            Button back = new Button("\u2190  Back to Dashboard",
                new Color(255, 255, 255, 235), GREEN_DARK);
            back.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) {
                    w.dispose();
                }
                RoleBasedDashboard.showFront();
            });
            right.add(back);

            add(left, BorderLayout.WEST);
            add(right, BorderLayout.EAST);
        }

        /** Adds a component to the right side (the back button always stays last). */
        public void addRight(Component c) {
            right.add(c, Math.max(0, right.getComponentCount() - 1));
            right.revalidate();
            right.repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new LinearGradientPaint(0, 0, w, 0,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(10, 70, 48), new Color(24, 112, 76),
                    new Color(58, 160, 110)}));
            g2.fillRect(0, 0, w, h);

            // soft light circles
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fill(new Ellipse2D.Double(w * 0.52, -h * 1.1, h * 2.6, h * 2.6));
            g2.setColor(new Color(255, 255, 255, 14));
            g2.fill(new Ellipse2D.Double(w * 0.72, h * 0.1, h * 1.8, h * 1.8));
            g2.setColor(new Color(200, 255, 190, 22));
            g2.fill(new Ellipse2D.Double(-h * 0.8, h * 0.4, h * 1.7, h * 1.7));

            // a few leaves
            for (int n = 0; n < 4; n++) {
                double lx = w * (0.60 + 0.08 * n);
                double ly = h * (0.25 + 0.2 * ((n * 7) % 4) / 3.0);
                double sz = 26 + 8 * (n % 3);
                java.awt.geom.Path2D leaf = new java.awt.geom.Path2D.Double();
                leaf.moveTo(0, 0);
                leaf.curveTo(sz * 0.5, -sz * 0.6, sz * 1.1, -sz * 0.2, sz * 1.2, 0);
                leaf.curveTo(sz * 1.1, sz * 0.2, sz * 0.5, sz * 0.6, 0, 0);
                java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform
                    .getTranslateInstance(lx, ly);
                at.rotate(Math.toRadians(-40 + n * 25));
                g2.setColor(new Color(255, 255, 255, 26));
                g2.fill(at.createTransformedShape(leaf));
            }

            // glowing base line
            g2.setPaint(new GradientPaint(0, 0, new Color(170, 235, 130, 0),
                w / 2f, 0, new Color(170, 235, 130, 200)));
            g2.fillRect(0, h - 3, w / 2, 3);
            g2.setPaint(new GradientPaint(w / 2f, 0, new Color(170, 235, 130, 200),
                w, 0, new Color(170, 235, 130, 0)));
            g2.fillRect(w / 2, h - 3, w - w / 2, 3);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------
    // ProgressBar: thin rounded bar, value from 0.0 to 1.0
    // ---------------------------------------------------------------
    public static class ProgressBar extends JComponent {

        private double value;

        public ProgressBar() {
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(340, 10));
        }

        public void setValue(double v) {
            value = Math.max(0, Math.min(1, v));
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 10);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(224, 232, 227));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
            if (value > 0) {
                g2.setColor(GREEN);
                g2.fill(new RoundRectangle2D.Double(
                    0, 0, Math.max(h, w * value), h, h, h));
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------
    // Backdrop: soft mint gradient page with faint circles
    // ---------------------------------------------------------------
    public static class Backdrop extends JPanel {

        public Backdrop(LayoutManager lm) {
            super(lm);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(226, 244, 234),
                w, h, new Color(246, 252, 248)));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(52, 150, 105, 20));
            g2.fill(new Ellipse2D.Double(-w * 0.10, h * 0.55, w * 0.45, w * 0.45));
            g2.setColor(new Color(120, 200, 150, 22));
            g2.fill(new Ellipse2D.Double(w * 0.65, -h * 0.15, w * 0.5, w * 0.5));
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fill(new Ellipse2D.Double(w * 0.35, h * 0.7, w * 0.4, w * 0.4));
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------
    // Slim rounded scroll bars for every screen
    // ---------------------------------------------------------------
    public static class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return new SlimScrollBarUI();
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(140, 186, 160);
            trackColor = new Color(0, 0, 0, 0);
        }

        private JButton none() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected JButton createDecreaseButton(int o) {
            return none();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return none();
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(12, 12);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // transparent
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            g2.setColor(isThumbRollover() ? new Color(70, 150, 105) : thumbColor);
            g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4,
                r.height - 4, 10, 10));
            g2.dispose();
        }
    }


    // ---------------------------------------------------------------
    // Flat rounded combo boxes for every screen
    // ---------------------------------------------------------------
    public static class FlatComboUI extends javax.swing.plaf.basic.BasicComboBoxUI {

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return new FlatComboUI();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
            @SuppressWarnings("unchecked")
            JComboBox<Object> box = (JComboBox<Object>) c;
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object v,
                        int index, boolean sel, boolean focus) {
                    super.getListCellRendererComponent(list, v, index, sel, focus);
                    setOpaque(index >= 0);
                    setBackground(sel ? GREEN_SOFT : Color.WHITE);
                    setForeground(TEXT);
                    setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                    return this;
                }
            });
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    quality(g2);
                    g2.setColor(GREEN);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g2.dispose();
                }
            };
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setPreferredSize(new Dimension(30, 30));
            return b;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean hasFocus) {
            // painted in paint()
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            quality(g2);
            int w = c.getWidth();
            int h = c.getHeight();
            boolean focus = comboBox.hasFocus() || comboBox.isPopupVisible();
            if (focus) {
                g2.setColor(new Color(34, 120, 84, 50));
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            }
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 12, 12));
            g2.setColor(focus ? GREEN : INPUT_BORDER);
            g2.setStroke(new BasicStroke(focus ? 2.2f : 1.5f));
            g2.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 12, 12));
            g2.dispose();
            super.paint(g, c);
        }
    }

    /** Gives one combo box the flat rounded look (no UIManager lookup, safe in modules). */
    public static <T extends JComboBox<?>> T flat(T c) {
        c.setUI(new FlatComboUI());
        return c;
    }

    /** Gives a scroll pane the slim rounded scroll bars. */
    public static JScrollPane slim(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        return sp;
    }
}