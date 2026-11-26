package rebirth;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * All the drawing for REbirth Compass. Everything is Java2D: no images.
 *
 *   RadarChart   six-sided compass, hover + click a pillar, grows in
 *   RingGauge    ring that fills toward the Readiness Index (count-up)
 *   GrowthTree   a living tree: size = index, six roots = six pillars
 *   HeatGrid     district tiles coloured by average readiness
 *   PillarBars   statewide pillar averages, weakest one glows
 *   KpiCard      big animated number card
 *   BandDonut    how many people sit in each readiness band
 */
public final class CompassVisuals {

    private CompassVisuals() { }

    // ------------------------------------------------------------------
    // Colours and small helpers
    // ------------------------------------------------------------------
    public static final Color[] PILLAR_COLORS = {
        new Color(43, 108, 176),   // Documents  - blue
        new Color(124, 77, 160),   // Skills     - purple
        new Color(214, 120, 36),   // Livelihood - orange
        new Color(0, 144, 156),    // Mind       - teal
        new Color(205, 70, 115),   // Family     - rose
        new Color(176, 136, 24)    // Legal      - gold
    };

    public static final Color[] BAND_COLORS = {
        new Color(200, 58, 62), new Color(224, 134, 40),
        new Color(86, 172, 92), new Color(20, 134, 86)
    };

    // dark "control room" palette (Command Centre)
    public static final Color D_BG1 = new Color(10, 24, 22);
    public static final Color D_BG2 = new Color(18, 46, 42);
    public static final Color D_CARD = new Color(24, 52, 47);
    public static final Color D_LINE = new Color(52, 92, 82);
    public static final Color D_TEXT = new Color(232, 245, 238);
    public static final Color D_MUTED = new Color(150, 184, 172);
    public static final Color D_NEON = new Color(72, 232, 170);

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
            Math.max(0, Math.min(255, a)));
    }

    public static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Red -> orange -> yellow -> green for a 0-100 value. */
    public static Color heat(double v) {
        Color[] c = {
            new Color(196, 52, 58), new Color(226, 124, 44),
            new Color(214, 188, 56), new Color(92, 176, 96), new Color(22, 140, 90)
        };
        double[] at = {0, 35, 60, 80, 100};
        v = Math.max(0, Math.min(100, v));
        for (int i = 0; i < 4; i++) {
            if (v <= at[i + 1]) {
                return lerp(c[i], c[i + 1], (v - at[i]) / (at[i + 1] - at[i]));
            }
        }
        return c[4];
    }

    public static Color bandColor(double idx) {
        return BAND_COLORS[CompassEngine.band(idx)];
    }

    static double clamp01(double t) {
        return Math.max(0, Math.min(1, t));
    }

    static double easeOut(double t) {
        t = clamp01(t);
        return 1 - Math.pow(1 - t, 3);
    }

    static double easeOutBack(double t) {
        t = clamp01(t);
        double c1 = 1.1;
        double u = t - 1;
        return 1 + (c1 + 1) * u * u * u + c1 * u * u;
    }

    static void quality(Graphics2D g) {
        Theme.quality(g);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
    }

    static void centered(Graphics2D g, String s, double cx, double baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) baseline);
    }

    // ------------------------------------------------------------------
    // Base: a panel with a 0..1 "intro" value and a light timer
    // ------------------------------------------------------------------
    public abstract static class AnimPanel extends JPanel {

        protected double t = 0;
        protected double clock = 0;
        protected boolean continuous = false;
        protected int durationMs = 1000;
        private long startMs = System.currentTimeMillis();
        private final Timer timer = new Timer(30, e -> tick());

        protected AnimPanel() {
            setOpaque(false);
        }

        public void replay() {
            startMs = System.currentTimeMillis();
            t = 0;
            repaint();
        }

        private void tick() {
            long now = System.currentTimeMillis();
            boolean intro = t < 1;
            if (intro) {
                t = Math.min(1, (now - startMs) / (double) durationMs);
            }
            clock = (now - startMs) / 1000.0;
            if (intro || continuous || wantsRepaint()) {
                repaint();
            }
        }

        protected boolean wantsRepaint() {
            return false;
        }

        @Override
        public void addNotify() {
            super.addNotify();
            timer.start();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }
    }

    // ------------------------------------------------------------------
    // RadarChart
    // ------------------------------------------------------------------
    public static class RadarChart extends AnimPanel {

        private double[] vals = new double[6];
        private double[] prev;
        private int weakest = -1;
        private int hover = -1;
        private int selected = -1;
        private IntConsumer onSelect;
        private final Rectangle2D[] boxes = new Rectangle2D[6];

        public RadarChart() {
            durationMs = 1100;
            setPreferredSize(new Dimension(600, 480));

            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(
                            h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h >= 0) {
                        selected = h;
                        if (onSelect != null) {
                            onSelect.accept(h);
                        }
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void setOnSelect(IntConsumer c) {
            onSelect = c;
        }

        public void setSelected(int i) {
            selected = i;
            repaint();
        }

        public void setData(double[] v, double[] previous, int weakestPillar) {
            vals = v.clone();
            prev = previous == null ? null : previous.clone();
            weakest = weakestPillar;
            replay();
        }

        @Override
        protected boolean wantsRepaint() {
            return hover >= 0 || selected >= 0;
        }

        private double radius() {
            return Math.max(70, Math.min(getWidth() - 290, getHeight() - 130) / 2.0);
        }

        private Point2D pt(int i, double frac) {
            double a = Math.toRadians(-90 + 60 * i);
            double r = radius() * frac;
            return new Point2D.Double(getWidth() / 2.0 + r * Math.cos(a),
                getHeight() / 2.0 + 4 + r * Math.sin(a));
        }

        private int hit(Point p) {
            for (int i = 0; i < 6; i++) {
                if (boxes[i] != null && boxes[i].contains(p)) {
                    return i;
                }
                if (pt(i, Math.max(0.05, vals[i] / 100.0)).distance(p) < 16) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);

            // rings
            for (int k = 4; k >= 1; k--) {
                Path2D hex = new Path2D.Double();
                for (int i = 0; i < 6; i++) {
                    Point2D p = pt(i, k / 4.0);
                    if (i == 0) {
                        hex.moveTo(p.getX(), p.getY());
                    } else {
                        hex.lineTo(p.getX(), p.getY());
                    }
                }
                hex.closePath();
                g.setColor(k % 2 == 0 ? new Color(236, 246, 240) : new Color(247, 251, 248));
                g.fill(hex);
                g.setColor(new Color(196, 214, 204));
                g.setStroke(new BasicStroke(1f));
                g.draw(hex);
            }
            g.setFont(Theme.f(Font.PLAIN, 10));
            g.setColor(Theme.MUTED);
            for (int k = 1; k <= 3; k++) {
                Point2D p = pt(0, k / 4.0);
                g.drawString(String.valueOf(k * 25), (float) p.getX() + 4, (float) p.getY() + 11);
            }

            // spokes
            for (int i = 0; i < 6; i++) {
                Point2D e = pt(i, 1);
                boolean on = i == selected || i == hover;
                g.setColor(on ? PILLAR_COLORS[i] : new Color(196, 214, 204));
                g.setStroke(new BasicStroke(on ? 2.6f : 1f));
                g.draw(new Line2D.Double(getWidth() / 2.0, getHeight() / 2.0 + 4,
                    e.getX(), e.getY()));
            }

            double e = easeOutBack(t);

            // earlier compass (ghost)
            if (prev != null) {
                Path2D pp = polygon(prev, easeOut(t));
                g.setColor(new Color(120, 130, 150, 40));
                g.fill(pp);
                g.setColor(new Color(110, 120, 140, 190));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f));
                g.draw(pp);
            }

            // current compass
            Path2D poly = polygon(vals, e);
            Point2D c = new Point2D.Double(getWidth() / 2.0, getHeight() / 2.0 + 4);
            g.setPaint(new RadialGradientPaint(c, (float) radius(),
                new float[]{0f, 1f},
                new Color[]{alpha(Theme.GREEN, 40), alpha(Theme.GREEN, 150)}));
            g.fill(poly);
            g.setColor(Theme.GREEN_DARK);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(poly);

            // vertices
            for (int i = 0; i < 6; i++) {
                Point2D p = pt(i, vals[i] / 100.0 * e);
                boolean on = i == selected || i == hover;
                if (on) {
                    double pulse = 11 + 4 * Math.sin(clock * 5);
                    g.setColor(alpha(PILLAR_COLORS[i], 70));
                    g.fill(new Ellipse2D.Double(p.getX() - pulse, p.getY() - pulse,
                        pulse * 2, pulse * 2));
                }
                double r = on ? 8 : 6;
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(p.getX() - r - 2, p.getY() - r - 2,
                    (r + 2) * 2, (r + 2) * 2));
                g.setColor(PILLAR_COLORS[i]);
                g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));
            }

            // labels
            for (int i = 0; i < 6; i++) {
                drawLabel(g, i);
            }
            g.dispose();
        }

        private Path2D polygon(double[] v, double e) {
            Path2D poly = new Path2D.Double();
            for (int i = 0; i < 6; i++) {
                Point2D p = pt(i, v[i] / 100.0 * e);
                if (i == 0) {
                    poly.moveTo(p.getX(), p.getY());
                } else {
                    poly.lineTo(p.getX(), p.getY());
                }
            }
            poly.closePath();
            return poly;
        }

        private void drawLabel(Graphics2D g, int i) {
            boolean on = i == selected || i == hover;
            String name = CompassEngine.PILLARS[i];
            String num = String.valueOf(Math.round(vals[i] * easeOut(t)));

            g.setFont(Theme.f(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int w = Math.max(fm.stringWidth(name), 34) + 26;
            int h = 44;

            Point2D a = pt(i, 1);
            double dx = Math.cos(Math.toRadians(-90 + 60 * i));
            double dy = Math.sin(Math.toRadians(-90 + 60 * i));
            double cx = a.getX() + dx * (w / 2.0 + 8);
            double cy = a.getY() + dy * (h / 2.0 + 10);
            if (Math.abs(dx) < 0.1) {
                cx = a.getX();
            }
            cx = Math.max(w / 2.0 + 4, Math.min(getWidth() - w / 2.0 - 4, cx));
            Rectangle2D box = new Rectangle2D.Double(cx - w / 2.0, cy - h / 2.0, w, h);
            boxes[i] = box;

            RoundRectangle2D rr = new RoundRectangle2D.Double(
                box.getX(), box.getY(), w, h, 16, 16);
            g.setColor(new Color(0, 0, 0, on ? 36 : 18));
            g.fill(new RoundRectangle2D.Double(box.getX(), box.getY() + 2, w, h, 16, 16));
            g.setColor(on ? lerp(Color.WHITE, PILLAR_COLORS[i], 0.12) : Color.WHITE);
            g.fill(rr);
            g.setColor(on ? PILLAR_COLORS[i] : new Color(206, 220, 211));
            g.setStroke(new BasicStroke(on ? 2.2f : 1f));
            g.draw(rr);

            boolean weak = i == weakest;
            g.setFont(Theme.f(Font.BOLD, 12));
            g.setColor(weak ? Theme.RED : Theme.TEXT);
            centered(g, name, cx, box.getY() + 17);
            g.setFont(Theme.f(Font.BOLD, 17));
            g.setColor(weak ? Theme.RED : PILLAR_COLORS[i]);
            centered(g, num, cx, box.getY() + 37);

            if (weak) {
                g.setColor(Theme.RED);
                g.fill(new Ellipse2D.Double(box.getMaxX() - 13, box.getY() + 6, 7, 7));
            }
        }
    }

    // ------------------------------------------------------------------
    // RingGauge
    // ------------------------------------------------------------------
    public static class RingGauge extends AnimPanel {

        private double value;
        private String band = "";

        public RingGauge() {
            durationMs = 1400;
            continuous = true;
            setPreferredSize(new Dimension(280, 300));
        }

        public void setValue(double v, String bandText) {
            value = v;
            band = bandText;
            replay();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);

            int w = getWidth();
            int h = getHeight();
            double size = Math.min(w - 40, h - 70);
            double cx = w / 2.0;
            double cy = 18 + size / 2.0;
            double stroke = Math.max(14, size * 0.085);
            double r = size / 2.0 - stroke;
            Color col = bandColor(value);
            double e = easeOut(t);
            double frac = value / 100.0 * e;

            Ellipse2D track = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(226, 234, 229));
            g.setStroke(new BasicStroke((float) stroke));
            g.draw(track);

            // threshold ticks at 35 / 60 / 80
            for (double th : new double[]{35, 60, 80}) {
                double a = Math.toRadians(90 - 360 * th / 100.0);
                double r1 = r + stroke / 2 + 3;
                double r2 = r1 + 7;
                g.setColor(new Color(150, 165, 157));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(cx + r1 * Math.cos(a), cy - r1 * Math.sin(a),
                    cx + r2 * Math.cos(a), cy - r2 * Math.sin(a)));
            }

            if (frac > 0.002) {
                Arc2D arc = new Arc2D.Double(cx - r, cy - r, r * 2, r * 2,
                    90, -360 * frac, Arc2D.OPEN);
                g.setColor(alpha(col, 50));
                g.setStroke(new BasicStroke((float) (stroke + 10),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(arc);
                g.setPaint(new GradientPaint((float) (cx - r), (float) (cy - r),
                    lerp(col, Color.WHITE, 0.25), (float) (cx + r), (float) (cy + r),
                    col.darker()));
                g.setStroke(new BasicStroke((float) stroke,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(arc);

                // glowing head
                double a = Math.toRadians(90 - 360 * frac);
                double hx = cx + r * Math.cos(a);
                double hy = cy - r * Math.sin(a);
                double pr = stroke * 0.55 + 2 * Math.sin(clock * 5);
                g.setColor(new Color(255, 255, 255, 200));
                g.fill(new Ellipse2D.Double(hx - pr / 2, hy - pr / 2, pr, pr));
            }

            g.setFont(Theme.f(Font.BOLD, 11));
            g.setColor(Theme.MUTED);
            centered(g, "READINESS INDEX", cx, cy - r * 0.42);

            g.setFont(Theme.f(Font.BOLD, (int) (size * 0.26)));
            g.setColor(col.darker());
            centered(g, String.valueOf(Math.round(value * e)), cx,
                cy + size * 0.07);

            g.setFont(Theme.f(Font.PLAIN, 13));
            g.setColor(Theme.MUTED);
            centered(g, "out of 100", cx, cy + size * 0.07 + 24);

            // band pill
            g.setFont(Theme.f(Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(band) + 30;
            int bh = 28;
            double by = cy + size / 2.0 + 10;
            g.setColor(alpha(col, 36));
            g.fill(new RoundRectangle2D.Double(cx - bw / 2.0, by, bw, bh, bh, bh));
            g.setColor(col);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Double(cx - bw / 2.0, by, bw, bh, bh, bh));
            g.setColor(col.darker());
            centered(g, band, cx, by + 19);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // GrowthTree  (size = index, roots = pillars)
    // ------------------------------------------------------------------
    public static class GrowthTree extends AnimPanel {

        private double index;
        private double[] pillars = new double[6];

        public GrowthTree() {
            durationMs = 1800;
            continuous = true;
            setPreferredSize(new Dimension(300, 470));
        }

        public void setData(double idx, double[] p) {
            index = idx;
            pillars = p.clone();
            replay();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);

            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 24, 24);
            g.setClip(shape);

            double groundY = h * 0.70;

            // sky
            g.setPaint(new GradientPaint(0, 0, new Color(214, 238, 232),
                0, (float) groundY, new Color(250, 253, 246)));
            g.fillRect(0, 0, w, (int) groundY + 2);

            // sun
            g.setColor(new Color(255, 236, 170, 120));
            g.fill(new Ellipse2D.Double(w - 96, 14, 70, 70));
            g.setColor(new Color(255, 226, 130, 200));
            g.fill(new Ellipse2D.Double(w - 84, 26, 46, 46));

            // hills
            g.setColor(new Color(176, 214, 176));
            g.fill(new Ellipse2D.Double(-w * 0.2, groundY - 46, w * 0.8, 120));
            g.setColor(new Color(150, 198, 160));
            g.fill(new Ellipse2D.Double(w * 0.35, groundY - 34, w * 0.9, 110));

            // soil
            g.setPaint(new GradientPaint(0, (float) groundY, new Color(118, 84, 58),
                0, h, new Color(66, 46, 34)));
            g.fillRect(0, (int) groundY, w, h - (int) groundY);
            g.setColor(new Color(92, 170, 96));
            g.setStroke(new BasicStroke(5f));
            g.draw(new Line2D.Double(0, groundY, w, groundY));

            double cx = w / 2.0;
            double grow = index / 100.0 * easeOut(t);

            drawRoots(g, cx, groundY, h);

            // tree
            if (index < 12) {
                drawSprout(g, cx, groundY, easeOut(t));
            } else {
                double avail = groundY - 40;
                double len = avail / 4.7 * (0.40 + 0.60 * grow / Math.max(0.12, index / 100.0));
                len *= Math.min(1.0, 0.45 + grow * 1.4);
                int maxDepth = 1 + (int) Math.round(grow * 6);
                double wid = 3 + 12 * grow;
                branch(g, cx, groundY, -Math.PI / 2, len, wid, 0, maxDepth,
                    new Random(42), grow);
            }

            // fireflies
            if (index >= 40) {
                Random fr = new Random(7);
                for (int i = 0; i < 10; i++) {
                    double bx = w * (0.12 + 0.76 * fr.nextDouble());
                    double by = groundY * (0.18 + 0.6 * fr.nextDouble());
                    double x = bx + 14 * Math.sin(clock * 0.8 + i * 1.7);
                    double y = by + 10 * Math.cos(clock * 0.9 + i * 2.3);
                    int a = (int) (110 + 110 * Math.sin(clock * 2.2 + i));
                    g.setColor(new Color(255, 240, 150, Math.max(30, a)));
                    g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                    g.setColor(new Color(255, 240, 150, Math.max(10, a / 4)));
                    g.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
                }
            }

            // stage pill
            int st = CompassEngine.stage(index);
            String s = "Stage " + (st + 1) + " of 6  \u00B7  " + CompassEngine.STAGES[st];
            g.setFont(Theme.f(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int pw = fm.stringWidth(s) + 24;
            g.setColor(new Color(255, 255, 255, 220));
            g.fill(new RoundRectangle2D.Double(12, 12, pw, 28, 28, 28));
            g.setColor(Theme.GREEN_DARK);
            g.drawString(s, 24, 31);

            g.setFont(Theme.f(Font.PLAIN, 11));
            g.setColor(new Color(235, 220, 200));
            String cap = "Roots = your six pillars";
            g.drawString(cap, w - g.getFontMetrics().stringWidth(cap) - 14, h - 10);

            g.setClip(null);
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(shape);
            g.dispose();
        }

        private void drawRoots(Graphics2D g, double cx, double groundY, int h) {
            double depth = h - groundY - 14;
            double e = easeOut(t);
            for (int i = 0; i < 6; i++) {
                double side = (i - 2.5) / 2.5;               // -1 .. 1
                double s = pillars[i] / 100.0;
                double ex = cx + side * (28 + 98 * (0.5 + 0.5 * s)) * e;
                double ey = groundY + depth * (0.38 + 0.62 * s) * e;
                double mx = cx + side * 20;
                double my = groundY + (ey - groundY) * 0.35;
                Path2D root = new Path2D.Double();
                root.moveTo(cx + side * 3, groundY);
                root.quadTo(mx, my, ex, ey);
                g.setColor(alpha(PILLAR_COLORS[i], 235));
                g.setStroke(new BasicStroke((float) (2 + 7 * s),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(root);
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(ex - 4, ey - 4, 8, 8));
                g.setColor(PILLAR_COLORS[i]);
                g.fill(new Ellipse2D.Double(ex - 3, ey - 3, 6, 6));
            }
        }

        private void drawSprout(Graphics2D g, double cx, double gy, double e) {
            g.setColor(new Color(100, 70, 48));
            g.fill(new Ellipse2D.Double(cx - 14, gy - 7, 28, 14));
            double hgt = 26 * e;
            g.setColor(new Color(96, 168, 88));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx, gy - 4, cx, gy - 4 - hgt));
            double sw = 3 * Math.sin(clock * 1.5);
            g.setColor(new Color(120, 196, 100));
            g.fill(new Ellipse2D.Double(cx - 20 + sw, gy - 4 - hgt - 6, 18, 10));
            g.fill(new Ellipse2D.Double(cx + 2 + sw, gy - 4 - hgt - 10, 18, 10));
        }

        private void branch(Graphics2D g, double x, double y, double ang, double len,
                double wid, int depth, int maxDepth, Random r, double grow) {

            if (depth > maxDepth || len < 3) {
                return;
            }
            double a = ang + Math.sin(clock * 1.3 + depth * 0.8 + x * 0.02)
                * (0.012 + 0.012 * depth);
            double x2 = x + Math.cos(a) * len;
            double y2 = y + Math.sin(a) * len;

            double f = depth / (double) Math.max(1, maxDepth);
            g.setColor(lerp(new Color(86, 58, 38), new Color(142, 104, 70), f));
            g.setStroke(new BasicStroke((float) Math.max(1.3, wid),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x, y, x2, y2));

            if (depth >= maxDepth - 1) {
                leaves(g, x2, y2, r, depth == maxDepth ? 6 : 3);
            }
            if (depth == maxDepth) {
                return;
            }

            int kids = depth == 0 ? 2 : (r.nextDouble() < 0.28 ? 3 : 2);
            for (int k = 0; k < kids; k++) {
                double spread = (k - (kids - 1) / 2.0) * (0.50 + r.nextDouble() * 0.18);
                double cl = len * (0.72 + r.nextDouble() * 0.10);
                branch(g, x2, y2, a + spread + (r.nextDouble() - 0.5) * 0.12, cl,
                    wid * 0.70, depth + 1, maxDepth, r, grow);
            }
        }

        private void leaves(Graphics2D g, double x, double y, Random r, int count) {
            double idx = index;
            Color base = idx < 35 ? new Color(160, 168, 72)
                : idx < 60 ? new Color(112, 176, 84)
                : idx < 80 ? new Color(70, 160, 88) : new Color(30, 140, 84);
            for (int i = 0; i < count; i++) {
                double dx = (r.nextDouble() - 0.5) * 26;
                double dy = (r.nextDouble() - 0.5) * 22;
                double s = 6 + r.nextDouble() * 6;
                Color c = lerp(base, new Color(190, 226, 120), r.nextDouble() * 0.45);
                g.setColor(alpha(c, 215));
                g.fill(new Ellipse2D.Double(x + dx - s / 2, y + dy - s / 2, s, s * 0.78));
                if (idx >= 80 && r.nextDouble() < 0.10) {
                    g.setColor(new Color(244, 150, 186));
                    g.fill(new Ellipse2D.Double(x + dx, y + dy, 5, 5));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // HeatGrid (district tiles)
    // ------------------------------------------------------------------
    public static class HeatGrid extends AnimPanel {

        private List<CompassEngine.District> ds = new java.util.ArrayList<>();
        private int hover = -1;
        private int selected = -1;
        private IntConsumer onSelect;
        private Rectangle2D[] rects = new Rectangle2D[0];
        private final int cols = 5;

        public HeatGrid() {
            durationMs = 1300;
            setPreferredSize(new Dimension(640, 330));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(
                            h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h >= 0) {
                        selected = selected == h ? -1 : h;
                        if (onSelect != null) {
                            onSelect.accept(selected);
                        }
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void setOnSelect(IntConsumer c) {
            onSelect = c;
        }

        public void setData(List<CompassEngine.District> d) {
            ds = d;
            selected = -1;
            replay();
        }

        private int hit(Point p) {
            for (int i = 0; i < rects.length; i++) {
                if (rects[i] != null && rects[i].contains(p)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected boolean wantsRepaint() {
            return hover >= 0;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);

            int n = ds.size();
            if (n == 0) {
                g.dispose();
                return;
            }
            int rows = (n + cols - 1) / cols;
            double gap = 12;
            double tw = (getWidth() - gap * (cols - 1)) / cols;
            double th = (getHeight() - gap * (rows - 1)) / rows;
            rects = new Rectangle2D[n];

            for (int i = 0; i < n; i++) {
                CompassEngine.District d = ds.get(i);
                double lt = easeOut(clamp01(t * 1.7 - (i % cols + i / cols) * 0.07));
                double x = (i % cols) * (tw + gap);
                double y = (i / cols) * (th + gap);
                rects[i] = new Rectangle2D.Double(x, y, tw, th);

                boolean on = i == hover || i == selected;
                double sc = (0.82 + 0.18 * lt) * (on ? 1.03 : 1.0);
                double rw = tw * sc;
                double rh = th * sc;
                double rx = x + (tw - rw) / 2;
                double ry = y + (th - rh) / 2 - (on ? 2 : 0);
                RoundRectangle2D rr = new RoundRectangle2D.Double(rx, ry, rw, rh, 20, 20);

                Composite old = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) clamp01(lt)));

                boolean empty = d.n == 0;
                if (empty || d.hidden) {
                    g.setColor(D_CARD);
                    g.fill(rr);
                    g.setColor(D_LINE);
                    g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 5f}, 0f));
                    g.draw(rr);
                    g.setColor(D_MUTED);
                    g.setFont(Theme.f(Font.BOLD, 13));
                    centered(g, d.name, rx + rw / 2, ry + rh * 0.42);
                    g.setFont(Theme.f(Font.PLAIN, 11));
                    centered(g, empty ? "No data" : "Hidden (privacy)",
                        rx + rw / 2, ry + rh * 0.42 + 20);
                } else {
                    Color c = heat(d.avg);
                    if (on) {
                        g.setColor(alpha(c, 90));
                        g.fill(new RoundRectangle2D.Double(rx - 5, ry - 5, rw + 10, rh + 10, 26, 26));
                    }
                    g.setPaint(new GradientPaint((float) rx, (float) ry, lerp(c, Color.WHITE, 0.18),
                        (float) rx, (float) (ry + rh), c.darker()));
                    g.fill(rr);
                    g.setColor(on ? Color.WHITE : alpha(Color.WHITE, 70));
                    g.setStroke(new BasicStroke(on ? 3f : 1.2f));
                    g.draw(rr);

                    g.setColor(Color.WHITE);
                    g.setFont(Theme.f(Font.BOLD, 13));
                    centered(g, d.name, rx + rw / 2, ry + 22);
                    g.setFont(Theme.f(Font.BOLD, (int) Math.max(20, rh * 0.34)));
                    centered(g, String.valueOf(Math.round(d.avg * lt)), rx + rw / 2,
                        ry + rh * 0.66);
                    g.setFont(Theme.f(Font.PLAIN, 11));
                    g.setColor(new Color(255, 255, 255, 220));
                    centered(g, d.n + (d.n == 1 ? " person" : " people"),
                        rx + rw / 2, ry + rh - 11);
                }
                g.setComposite(old);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // PillarBars (statewide / district averages)
    // ------------------------------------------------------------------
    public static class PillarBars extends AnimPanel {

        private double[] vals = new double[6];
        private int weakest = -1;
        private int hover = -1;
        private int selected = -1;
        private IntConsumer onSelect;

        public PillarBars() {
            durationMs = 1200;
            setPreferredSize(new Dimension(520, 300));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = row(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(
                            h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int h = row(e.getPoint());
                    if (h >= 0) {
                        selected = h;
                        if (onSelect != null) {
                            onSelect.accept(h);
                        }
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void setOnSelect(IntConsumer c) {
            onSelect = c;
        }

        public void setData(double[] v, int weakestPillar) {
            vals = v.clone();
            weakest = weakestPillar;
            replay();
        }

        private int row(Point p) {
            int i = (int) (p.getY() / (getHeight() / 6.0));
            return i >= 0 && i < 6 ? i : -1;
        }

        @Override
        protected boolean wantsRepaint() {
            return weakest >= 0;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);

            double rh = getHeight() / 6.0;
            double labelW = 128;
            double trackX = labelW + 8;
            double trackW = getWidth() - trackX - 56;

            for (int i = 0; i < 6; i++) {
                double y = i * rh;
                double lt = easeOut(clamp01(t * 1.5 - i * 0.08));
                boolean weak = i == weakest;
                boolean on = i == hover || i == selected;

                if (on) {
                    g.setColor(new Color(255, 255, 255, 14));
                    g.fill(new RoundRectangle2D.Double(0, y + 2, getWidth(), rh - 4, 14, 14));
                }

                g.setFont(Theme.f(Font.BOLD, 13));
                g.setColor(weak ? new Color(255, 140, 140) : D_TEXT);
                g.drawString(CompassEngine.PILLARS[i], 8, (float) (y + rh / 2 + 5));

                double bh = Math.min(16, rh * 0.36);
                double by = y + (rh - bh) / 2;
                g.setColor(new Color(255, 255, 255, 24));
                g.fill(new RoundRectangle2D.Double(trackX, by, trackW, bh, bh, bh));

                double fw = Math.max(bh, trackW * vals[i] / 100.0 * lt);
                Color c = PILLAR_COLORS[i];
                if (weak) {
                    double pulse = 0.5 + 0.5 * Math.sin(clock * 4);
                    g.setColor(new Color(255, 80, 80, (int) (40 + 50 * pulse)));
                    g.fill(new RoundRectangle2D.Double(trackX - 4, by - 4, fw + 8, bh + 8,
                        bh + 8, bh + 8));
                }
                g.setPaint(new GradientPaint((float) trackX, 0, lerp(c, Color.WHITE, 0.25),
                    (float) (trackX + fw), 0, c));
                g.fill(new RoundRectangle2D.Double(trackX, by, fw, bh, bh, bh));

                g.setFont(Theme.f(Font.BOLD, 13));
                g.setColor(D_TEXT);
                g.drawString(String.valueOf(Math.round(vals[i] * lt)),
                    (float) (trackX + trackW + 12), (float) (y + rh / 2 + 5));

                if (weak) {
                    g.setFont(Theme.f(Font.BOLD, 10));
                    String tag = "BIGGEST GAP";
                    FontMetrics fm = g.getFontMetrics();
                    double tx = trackX + Math.min(fw + 12, trackW - fm.stringWidth(tag) - 14);
                    g.setColor(new Color(220, 60, 60));
                    g.fill(new RoundRectangle2D.Double(tx, by - 1, fm.stringWidth(tag) + 12,
                        bh + 2, 10, 10));
                    g.setColor(Color.WHITE);
                    g.drawString(tag, (float) (tx + 6), (float) (by + bh - 3));
                }
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // KpiCard
    // ------------------------------------------------------------------
    public static class KpiCard extends AnimPanel {

        private final String title;
        private final Color accent;
        private double target;
        private String suffix = "";
        private String sub = "";
        private boolean hover;

        public KpiCard(String title, Color accent) {
            this.title = title;
            this.accent = accent;
            durationMs = 1100;
            setPreferredSize(new Dimension(190, 112));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        public void setValue(double v, String suffixText, String subText) {
            target = v;
            suffix = suffixText;
            sub = subText;
            replay();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            double lift = hover ? 0 : 3;

            RoundRectangle2D rr = new RoundRectangle2D.Double(1, lift, w - 3, h - 8, 20, 20);
            if (hover) {
                g.setColor(alpha(accent, 60));
                g.fill(new RoundRectangle2D.Double(-3, -3, w + 5, h - 1, 26, 26));
            }
            g.setPaint(new GradientPaint(0, 0, D_CARD, 0, h, D_BG2));
            g.fill(rr);
            g.setColor(hover ? accent : D_LINE);
            g.setStroke(new BasicStroke(hover ? 2f : 1f));
            g.draw(rr);

            g.setColor(accent);
            g.fill(new RoundRectangle2D.Double(14, lift + 14, 28, 4, 4, 4));

            g.setFont(Theme.f(Font.BOLD, 11));
            g.setColor(D_MUTED);
            g.drawString(title.toUpperCase(), 14, (float) (lift + 36));

            g.setFont(Theme.f(Font.BOLD, 34));
            g.setColor(D_TEXT);
            String v = String.valueOf(Math.round(target * easeOut(t)));
            g.drawString(v + suffix, 14, (float) (lift + 74));

            g.setFont(Theme.f(Font.PLAIN, 11));
            g.setColor(accent);
            g.drawString(sub, 14, (float) (lift + 92));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // BandDonut
    // ------------------------------------------------------------------
    public static class BandDonut extends AnimPanel {

        private int[] counts = new int[4];

        public BandDonut() {
            durationMs = 1300;
            setPreferredSize(new Dimension(380, 190));
        }

        public void setData(int[] c) {
            counts = c.clone();
            replay();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int total = 0;
            for (int c : counts) {
                total += c;
            }
            double size = Math.min(getHeight() - 16, 170);
            double cx = 14 + size / 2;
            double cy = getHeight() / 2.0;
            double stroke = size * 0.18;
            double r = size / 2 - stroke / 2;

            g.setColor(new Color(255, 255, 255, 22));
            g.setStroke(new BasicStroke((float) stroke));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

            double start = 90;
            double e = easeOut(t);
            for (int i = 0; i < 4 && total > 0; i++) {
                double ext = -360.0 * counts[i] / total * e;
                if (counts[i] > 0) {
                    g.setColor(BAND_COLORS[i]);
                    g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND));
                    g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2,
                        start, ext + (ext < -3 ? 1.5 : 0), Arc2D.OPEN));
                }
                start += ext;
            }

            g.setFont(Theme.f(Font.BOLD, 26));
            g.setColor(D_TEXT);
            centered(g, String.valueOf(Math.round(total * e)), cx, cy + 6);
            g.setFont(Theme.f(Font.PLAIN, 10));
            g.setColor(D_MUTED);
            centered(g, "PEOPLE", cx, cy + 22);

            double lx = cx + size / 2 + 26;
            for (int i = 3; i >= 0; i--) {
                double ly = cy - 52 + (3 - i) * 36;
                g.setColor(BAND_COLORS[i]);
                g.fill(new RoundRectangle2D.Double(lx, ly, 14, 14, 5, 5));
                g.setFont(Theme.f(Font.BOLD, 13));
                g.setColor(D_TEXT);
                g.drawString(String.valueOf(counts[i]), (float) lx + 24, (float) ly + 12);
                g.setFont(Theme.f(Font.PLAIN, 12));
                g.setColor(D_MUTED);
                g.drawString(CompassEngine.BANDS[i], (float) lx + 50, (float) ly + 12);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // TnMap : Tamil Nadu drawn in Java2D, one bubble per district
    //   layer 0 = rehabilitation centres / NGOs / training partners
    //   layer 1 = average readiness (from the Compass)
    // ------------------------------------------------------------------
    public static class TnMap extends AnimPanel {

        private static final double[][] OUTLINE = {
            {80.32, 13.45}, {80.15, 13.58}, {79.75, 13.55}, {79.45, 13.35},
            {79.15, 13.35}, {78.75, 13.25}, {78.45, 13.05}, {78.25, 12.95},
            {77.95, 12.85}, {77.80, 12.55}, {77.85, 12.20}, {77.70, 11.95},
            {77.85, 11.75}, {77.60, 11.60}, {77.25, 11.55}, {76.90, 11.70},
            {76.55, 11.62}, {76.15, 11.45}, {76.45, 11.20}, {76.75, 11.20},
            {76.90, 10.95}, {76.85, 10.60}, {76.95, 10.30}, {77.15, 10.05},
            {77.25, 9.80}, {77.30, 9.55}, {77.40, 9.25}, {77.35, 8.95},
            {77.30, 8.70}, {77.15, 8.45}, {77.35, 8.15}, {77.55, 8.08},
            {77.90, 8.15}, {78.05, 8.40}, {78.15, 8.75}, {78.45, 9.05},
            {78.85, 9.25}, {79.15, 9.28}, {79.30, 9.30}, {79.18, 9.45},
            {79.05, 9.55}, {79.00, 9.80}, {79.25, 10.05}, {79.55, 10.30},
            {79.85, 10.30}, {79.87, 10.75}, {79.85, 11.15}, {79.77, 11.75},
            {79.95, 12.25}, {80.20, 12.60}, {80.25, 13.05}
        };
        public static final Color[] TYPE_COLORS = {
            new Color(30, 150, 96), new Color(52, 120, 204), new Color(232, 140, 40)
        };
        private static final int[] LABEL_SIDE = {1, -1, 1, 0, 1, -1, -1, -1, 1, 1, -1, 1, 1, -1, 0};

        private int[][] counts = new int[CompassEngine.DISTRICTS.length][3];
        private double[] avg = new double[CompassEngine.DISTRICTS.length];
        private int[] people = new int[CompassEngine.DISTRICTS.length];
        private boolean[] hidden = new boolean[CompassEngine.DISTRICTS.length];
        private int layer = 0;
        private int hover = -1;
        private int selected = -1;
        private IntConsumer onSelect;
        private final Point2D[] pos = new Point2D[CompassEngine.DISTRICTS.length];
        private final double[] rad = new double[CompassEngine.DISTRICTS.length];

        public TnMap() {
            durationMs = 1500;
            setPreferredSize(new Dimension(520, 520));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(
                            h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h >= 0) {
                        selected = selected == h ? -1 : h;
                        if (onSelect != null) {
                            onSelect.accept(selected);
                        }
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void setOnSelect(IntConsumer c) {
            onSelect = c;
        }

        public void setFacilities(int[][] c) {
            counts = c;
            replay();
        }

        public void setReadiness(double[] a, int[] n, boolean[] h) {
            avg = a;
            people = n;
            hidden = h;
            repaint();
        }

        public void setLayer(int l) {
            layer = l;
            replay();
        }

        @Override
        protected boolean wantsRepaint() {
            return hover >= 0;
        }

        private int hit(Point p) {
            for (int i = 0; i < pos.length - 1; i++) {
                if (pos[i] != null && pos[i].distance(p) <= rad[i] + 4) {
                    return i;
                }
            }
            return -1;
        }

        private int total(int i) {
            return counts[i][0] + counts[i][1] + counts[i][2];
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();

            // sea
            RoundRectangle2D box = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 24, 24);
            g.setPaint(new GradientPaint(0, 0, new Color(206, 234, 244), 0, h,
                new Color(232, 246, 250)));
            g.fill(box);
            g.setClip(box);
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(1.2f));
            for (int k = 0; k < 9; k++) {
                double y = h * (0.1 + k * 0.1) + 4 * Math.sin(clock + k);
                Path2D wave = new Path2D.Double();
                wave.moveTo(0, y);
                for (int x = 0; x <= w; x += 20) {
                    wave.lineTo(x, y + 3 * Math.sin(x * 0.04 + clock * 1.2 + k));
                }
                g.draw(wave);
            }

            // projection
            double lon0 = 75.9;
            double lon1 = 80.7;
            double lat0 = 7.8;
            double lat1 = 13.8;
            double kx = 0.98;
            double margin = 26;
            double k = Math.min((w - 2 * margin) / ((lon1 - lon0) * kx),
                (h - 2 * margin) / (lat1 - lat0));
            double ox = (w - (lon1 - lon0) * kx * k) / 2.0;
            double oy = (h - (lat1 - lat0) * k) / 2.0;

            Path2D state = new Path2D.Double();
            for (int i = 0; i < OUTLINE.length; i++) {
                double x = ox + (OUTLINE[i][0] - lon0) * kx * k;
                double y = oy + (lat1 - OUTLINE[i][1]) * k;
                if (i == 0) {
                    state.moveTo(x, y);
                } else {
                    state.lineTo(x, y);
                }
            }
            state.closePath();

            // soft shadow, fill, outline
            AffineTransform sh = AffineTransform.getTranslateInstance(5, 7);
            g.setColor(new Color(10, 60, 50, 50));
            g.fill(sh.createTransformedShape(state));
            g.setPaint(new GradientPaint(0, (float) oy, new Color(150, 214, 170),
                0, (float) (oy + (lat1 - lat0) * k), new Color(62, 160, 112)));
            g.fill(state);
            g.setColor(new Color(255, 255, 255, 120));
            g.setStroke(new BasicStroke(1f));
            Shape old = g.getClip();
            g.setClip(state);
            for (int i = -h; i < w + h; i += 26) {
                g.draw(new Line2D.Double(i, h, i + h, 0));
            }
            g.setClip(old);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(state);
            g.setColor(new Color(20, 110, 78));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(state);

            // names of the surroundings
            g.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            g.setColor(new Color(90, 130, 150));
            g.drawString("BAY OF BENGAL", (float) (w - 118), (float) (h * 0.55));
            centered(g, "INDIAN OCEAN", w * 0.55, h - 14);
            g.setColor(new Color(110, 140, 128));
            g.drawString("KERALA", 12, (float) (h * 0.62));
            g.drawString("KARNATAKA", 12, (float) (h * 0.16));
            centered(g, "ANDHRA PRADESH", w * 0.62, 20);

            // bubbles
            int n = CompassEngine.DISTRICTS.length - 1;
            for (int i = 0; i < n; i++) {
                double[] ll = CompassEngine.DISTRICT_LL[i];
                double x = ox + (ll[0] - lon0) * kx * k;
                double y = oy + (lat1 - ll[1]) * k;
                pos[i] = new Point2D.Double(x, y);
                double lt = easeOutBack(clamp01(t * 1.8 - i * 0.05));
                boolean on = i == hover || i == selected;
                double base;
                if (layer == 0) {
                    base = total(i) == 0 ? 6 : 10 + 3.2 * Math.sqrt(total(i));
                } else {
                    base = people[i] == 0 ? 6 : 14 + Math.min(6, people[i] * 0.5);
                }
                base = Math.min(base, 22);
                double r = base * Math.max(0, lt) * (on ? 1.14 : 1);
                rad[i] = base;
                drawBubble(g, i, x, y, r, on);
            }

            // labels
            g.setFont(Theme.f(Font.BOLD, 10));
            for (int i = 0; i < n; i++) {
                if (pos[i] == null) {
                    continue;
                }
                String name = CompassEngine.DISTRICTS[i];
                FontMetrics fm = g.getFontMetrics();
                double lx = pos[i].getX();
                double ly = pos[i].getY() + rad[i] + 12;
                if (LABEL_SIDE[i] == 1) {
                    lx += rad[i] + 6 + fm.stringWidth(name) / 2.0;
                    ly = pos[i].getY() + 4;
                } else if (LABEL_SIDE[i] == -1) {
                    lx -= rad[i] + 6 + fm.stringWidth(name) / 2.0;
                    ly = pos[i].getY() + 4;
                }
                g.setColor(new Color(255, 255, 255, 190));
                g.fill(new RoundRectangle2D.Double(lx - fm.stringWidth(name) / 2.0 - 4,
                    ly - 11, fm.stringWidth(name) + 8, 15, 8, 8));
                g.setColor(new Color(16, 70, 52));
                centered(g, name, lx, ly);
            }

            if (hover >= 0) {
                drawTip(g, hover, w, h);
            }
            g.setClip(null);
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(box);
            g.dispose();
        }

        private void drawBubble(Graphics2D g, int i, double x, double y, double r, boolean on) {

            if (r < 1) {
                return;
            }
            boolean empty = layer == 0 ? total(i) == 0 : people[i] == 0;
            if (empty) {
                g.setColor(new Color(255, 255, 255, 170));
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                g.setColor(new Color(120, 150, 138));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                return;
            }
            if (on) {
                double pr = r + 7 + 3 * Math.sin(clock * 5);
                g.setColor(new Color(255, 255, 255, 120));
                g.fill(new Ellipse2D.Double(x - pr, y - pr, pr * 2, pr * 2));
            }
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new Ellipse2D.Double(x - r + 1, y - r + 3, r * 2, r * 2));

            if (layer == 0) {
                int tot = total(i);
                double start = 90;
                double ring = Math.max(5, r * 0.38);
                for (int ty = 0; ty < 3; ty++) {
                    if (counts[i][ty] == 0) {
                        continue;
                    }
                    double ext = -360.0 * counts[i][ty] / tot;
                    g.setColor(TYPE_COLORS[ty]);
                    g.setStroke(new BasicStroke((float) ring, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND));
                    g.draw(new Arc2D.Double(x - r + ring / 2, y - r + ring / 2,
                        r * 2 - ring, r * 2 - ring, start, ext, Arc2D.OPEN));
                    start += ext;
                }
                g.setColor(Color.WHITE);
                double ir = r - ring;
                g.fill(new Ellipse2D.Double(x - ir, y - ir, ir * 2, ir * 2));
                g.setColor(new Color(16, 70, 52));
                g.setFont(Theme.f(Font.BOLD, (int) Math.max(10, r * 0.62)));
                centered(g, String.valueOf(tot), x, y + r * 0.22);
            } else if (hidden[i]) {
                g.setColor(new Color(110, 125, 120));
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                g.setColor(Color.WHITE);
                g.setFont(Theme.f(Font.BOLD, 14));
                centered(g, "?", x, y + 5);
            } else {
                Color c = heat(avg[i]);
                g.setPaint(new GradientPaint((float) x, (float) (y - r), lerp(c, Color.WHITE, 0.25),
                    (float) x, (float) (y + r), c.darker()));
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                g.setFont(Theme.f(Font.BOLD, (int) Math.max(11, r * 0.7)));
                centered(g, String.valueOf(Math.round(avg[i])), x, y + r * 0.25);
            }
        }

        private void drawTip(Graphics2D g, int i, int w, int h) {

            String[] lines;
            if (layer == 0) {
                lines = new String[]{CompassEngine.DISTRICTS[i],
                    "Rehabilitation centres: " + counts[i][0],
                    "NGO partners: " + counts[i][1],
                    "Training partners: " + counts[i][2]};
            } else if (people[i] == 0) {
                lines = new String[]{CompassEngine.DISTRICTS[i], "No people recorded"};
            } else if (hidden[i]) {
                lines = new String[]{CompassEngine.DISTRICTS[i], "Hidden for privacy",
                    "(group is too small)"};
            } else {
                lines = new String[]{CompassEngine.DISTRICTS[i],
                    "Average readiness: " + Math.round(avg[i]) + " / 100",
                    people[i] + " people"};
            }
            g.setFont(Theme.f(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int tw = 0;
            for (String l : lines) {
                tw = Math.max(tw, fm.stringWidth(l));
            }
            int bw = tw + 24;
            int bh = lines.length * 18 + 14;
            double bx = pos[i].getX() + rad[i] + 12;
            if (bx + bw > w - 6) {
                bx = pos[i].getX() - rad[i] - 12 - bw;
            }
            double by = Math.max(6, Math.min(h - bh - 6, pos[i].getY() - bh / 2.0));
            g.setColor(new Color(0, 0, 0, 50));
            g.fill(new RoundRectangle2D.Double(bx + 2, by + 3, bw, bh, 14, 14));
            g.setColor(new Color(16, 44, 38, 240));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 14, 14));
            for (int k = 0; k < lines.length; k++) {
                g.setFont(Theme.f(k == 0 ? Font.BOLD : Font.PLAIN, 12));
                g.setColor(k == 0 ? D_NEON : D_TEXT);
                g.drawString(lines[k], (float) bx + 12, (float) (by + 22 + k * 18));
            }
        }
    }
}