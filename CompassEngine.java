package rebirth;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * REbirth Compass - all the logic, no screens.
 *
 *  1. Readiness Compass : six pillars (0-100 each) -> one Readiness Index
 *  2. Early Warning     : plain IF-rules, every flag explains itself
 *  3. Scheme Matcher    : weakest pillar -> schemes tagged for that pillar
 *  4. Command Centre    : anonymised totals for the government view
 *
 * Every number comes from your Oracle tables. No machine learning:
 * each score line shows "label: points / max", so anyone can check it.
 *
 * The index is a decision-support tool. It does NOT predict who will
 * reoffend, and the weights are a starting point that experts would adjust.
 */
public final class CompassEngine {

    private CompassEngine() { }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------
    public static final String[] PILLARS = {
        "Documents", "Skills & Education", "Livelihood",
        "Mind & Health", "Family & Home", "Legal"
    };
    public static final String[] SHORT = {
        "Documents", "Skills", "Livelihood", "Mind", "Family", "Legal"
    };
    /** Weight of each pillar in the index. Sum = 100. */
    public static final int[] WEIGHTS = {15, 15, 25, 20, 15, 10};

    public static final String[] DOC_TYPES = {
        "ID Proof", "Bank Account", "Address Proof", "Certificates"
    };
    public static final String[] DOC_STATES = {"Missing", "Applied", "Held"};
    public static final String[] HOUSING = {"Not recorded", "None", "Temporary", "Stable"};
    public static final String[] FAMILY = {"Not recorded", "None", "Occasional", "Regular"};
    public static final String[] CASES = {"Not recorded", "Pending", "Cleared"};
    public static final String[] PAROLE = {
        "Not recorded", "Violated", "At risk", "Met", "Not applicable"
    };

    /** Edit this list for your state. The grid shows 5 x 3 tiles. */
    public static final String[] DISTRICTS = {
        "Chennai", "Coimbatore", "Madurai", "Tiruchirappalli", "Salem",
        "Tirunelveli", "Erode", "Vellore", "Thoothukudi", "Thanjavur",
        "Dindigul", "Kanyakumari", "Cuddalore", "Virudhunagar", "Other"
    };

    /** Values shown in the scheme form's "pillar" box. */
    public static final String[] SCHEME_TAGS = {
        "Auto (by keywords)", "Documents", "Skills & Education", "Livelihood",
        "Mind & Health", "Family & Home", "Legal", "General"
    };

    /** A district with fewer people than this is hidden (privacy). */
    public static final int MIN_GROUP = 3;

    private static final String[] DEMO_DISTRICTS = {"Madurai", "Chennai", "Coimbatore"};

    // ------------------------------------------------------------------
    // Data classes
    // ------------------------------------------------------------------
    public static class Line {
        public final String label;
        public final double pts;
        public final double max;
        public final String note;

        Line(String label, double pts, double max, String note) {
            this.label = label;
            this.pts = pts;
            this.max = max;
            this.note = note;
        }
    }

    public static class Compass {
        public int prisonerId;
        public String name = "";
        public String district = "";
        public LocalDate release;
        public long daysSinceRelease = -1;

        // raw inputs (read from the database)
        public String education = "", skills = "", employment = "", counselling = "";
        public final Map<String, String> docs = new LinkedHashMap<>();
        public String housing = "Not recorded", family = "Not recorded";
        public String caseStatus = "Not recorded", parole = "Not recorded";
        public int trainingDone, trainingApplied, jobApplied, jobDone;
        public int eduGoalsTotal, eduGoalsDone, empGoalsTotal, empGoalsDone;
        public int sessionsDone;
        public double avgRating;
        public long lastSessionDays = -1;
        public int lastRating;
        public int pendingReqId = -1;
        public int pendingDays;
        public int overdueGoals;

        // results
        public final double[] score = new double[6];
        public final boolean[] assessed = new boolean[6];
        public final List<List<Line>> lines = new ArrayList<>();
        public double index;

        // earlier snapshot (for the "shrinking compass" rule)
        public double[] prev;
        public double prevIndex = -1;
        public LocalDate prevDate;

        public int weakest() {
            return weakestOf(score);
        }
    }

    public static class Alert {
        public Compass compass;
        public int points;
        public String level;       // HIGH, MEDIUM, WATCH
        public final List<String> reasons = new ArrayList<>();
        public String suggestion = "";
    }

    public static class SchemeInfo {
        public int id;
        public String name = "", type = "", dept = "", benefits = "";
        public int pillar = -1;        // -1 = general
        public boolean auto;           // pillar guessed from keywords
    }

    public static class Match {
        public int weakest;
        public int pillar = -1;
        public final List<SchemeInfo> schemes = new ArrayList<>();
        public boolean fallback;       // nothing tagged for the weakest pillar
    }

    public static class District {
        public String name;
        public int n;
        public double avg;
        public final double[] pillarAvg = new double[6];
        public boolean hidden;
    }

    public static class Stats {
        public final List<Compass> people = new ArrayList<>();
        public final List<District> districts = new ArrayList<>();
        public double avg;
        public final double[] pillarAvg = new double[6];
        public int weakest = -1;
        public int high, medium, watch;
        public int pendingOver7;
        public int activeSchemes;
        public final int[] schemesPerPillar = new int[6];
        public final int[] weakestCount = new int[6];
        public final int[] bands = new int[4];
        public final List<String> actions = new ArrayList<>();
        public int unassigned;
        public CentreStats centres = new CentreStats();
    }

    // ------------------------------------------------------------------
    // Tables (created automatically, same style as CounsellorUtil)
    // ------------------------------------------------------------------
    private static boolean ready = false;

    public static synchronized void ensure(Connection con) {
        if (ready) {
            return;
        }
        create(con, "DOCUMENT_STATUS",
            "CREATE TABLE DOCUMENT_STATUS ("
            + "DOC_ID NUMBER PRIMARY KEY, PRISONER_ID NUMBER NOT NULL, "
            + "DOC_TYPE VARCHAR2(30) NOT NULL, STATUS VARCHAR2(15), "
            + "UPDATED_DATE DATE DEFAULT SYSDATE, IS_DEMO NUMBER(1) DEFAULT 0)");

        create(con, "REINTEGRATION_STATUS",
            "CREATE TABLE REINTEGRATION_STATUS ("
            + "PRISONER_ID NUMBER PRIMARY KEY, HOUSING VARCHAR2(20), "
            + "FAMILY_CONTACT VARCHAR2(20), CASE_STATUS VARCHAR2(20), "
            + "PAROLE_STATUS VARCHAR2(20), UPDATED_DATE DATE DEFAULT SYSDATE, "
            + "IS_DEMO NUMBER(1) DEFAULT 0)");

        create(con, "PILLAR_SNAPSHOTS",
            "CREATE TABLE PILLAR_SNAPSHOTS ("
            + "SNAP_ID NUMBER PRIMARY KEY, PRISONER_ID NUMBER NOT NULL, "
            + "SNAP_DATE DATE NOT NULL, P1 NUMBER, P2 NUMBER, P3 NUMBER, "
            + "P4 NUMBER, P5 NUMBER, P6 NUMBER, READINESS NUMBER, "
            + "IS_DEMO NUMBER(1) DEFAULT 0)");

        create(con, "CENTRES",
            "CREATE TABLE CENTRES ("
            + "CENTRE_ID NUMBER PRIMARY KEY, CENTRE_NAME VARCHAR2(120 CHAR) NOT NULL, "
            + "CENTRE_TYPE VARCHAR2(30), DISTRICT VARCHAR2(40), "
            + "IS_DEMO NUMBER(1) DEFAULT 0)");

        alter(con, "ALTER TABLE PRISONER_DETAILS ADD DISTRICT VARCHAR2(40)");
        alter(con, "ALTER TABLE GOVERNMENT_SCHEMES ADD PILLAR VARCHAR2(30)");
        ready = true;
    }

    private static void create(Connection con, String table, String ddl) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " WHERE 1=0")) {
            return;
        } catch (SQLException missing) {
            // create below
        }
        alter(con, ddl);
    }

    private static void alter(Connection con, String ddl) {
        try (Statement st = con.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ignored) {
            // already exists / created by another window
        }
    }

    // ------------------------------------------------------------------
    // 1. COMPASS
    // ------------------------------------------------------------------
    public static Compass compute(Connection con, int pid) throws SQLException {

        ensure(con);
        Compass c = new Compass();
        c.prisonerId = pid;
        LocalDate today = LocalDate.now();

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT u.FULL_NAME, p.EDUCATION, p.SKILLS, p.RELEASE_DATE, "
                + "p.EMPLOYMENT_STATUS, p.COUNSELLING_REQUIRED, p.DISTRICT "
                + "FROM PRISONER_DETAILS p JOIN USERS u ON u.USER_ID = p.USER_ID "
                + "WHERE p.PRISONER_ID = ?")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Prisoner not found.");
                }
                c.name = nz(rs.getString(1));
                c.education = nz(rs.getString(2));
                c.skills = nz(rs.getString(3));
                java.sql.Date d = rs.getDate(4);
                if (d != null) {
                    c.release = d.toLocalDate();
                    c.daysSinceRelease = ChronoUnit.DAYS.between(c.release, today);
                }
                c.employment = nz(rs.getString(5));
                c.counselling = nz(rs.getString(6));
                c.district = nz(rs.getString(7));
            }
        }

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT DOC_TYPE, STATUS FROM DOCUMENT_STATUS WHERE PRISONER_ID = ?")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    c.docs.put(nz(rs.getString(1)), nz(rs.getString(2)));
                }
            }
        }

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT HOUSING, FAMILY_CONTACT, CASE_STATUS, PAROLE_STATUS "
                + "FROM REINTEGRATION_STATUS WHERE PRISONER_ID = ?")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    c.housing = orDefault(rs.getString(1));
                    c.family = orDefault(rs.getString(2));
                    c.caseStatus = orDefault(rs.getString(3));
                    c.parole = orDefault(rs.getString(4));
                }
            }
        }

        // counselling
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*), NVL(AVG(PROGRESS_RATING), 0), MAX(SESSION_DATE) "
                + "FROM COUNSELLING_SESSIONS "
                + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'COMPLETED'")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    c.sessionsDone = rs.getInt(1);
                    c.avgRating = rs.getDouble(2);
                    Timestamp t = rs.getTimestamp(3);
                    if (t != null) {
                        c.lastSessionDays = ChronoUnit.DAYS.between(
                            t.toLocalDateTime().toLocalDate(), today);
                    }
                }
            }
        }
        c.lastRating = safeInt(con,
            "SELECT PROGRESS_RATING FROM (SELECT PROGRESS_RATING FROM "
            + "COUNSELLING_SESSIONS WHERE PRISONER_ID = ? AND "
            + "UPPER(STATUS) = 'COMPLETED' AND PROGRESS_RATING IS NOT NULL "
            + "ORDER BY SESSION_DATE DESC) WHERE ROWNUM = 1", pid);

        // rehabilitation goals
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CATEGORY, STATUS, TARGET_DATE FROM REHABILITATION_PLANS "
                + "WHERE PRISONER_ID = ?")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cat = nz(rs.getString(1)).toLowerCase();
                    boolean done = "completed".equalsIgnoreCase(rs.getString(2));
                    java.sql.Date target = rs.getDate(3);
                    if (cat.equals("education") || cat.equals("skills")) {
                        c.eduGoalsTotal++;
                        if (done) {
                            c.eduGoalsDone++;
                        }
                    } else if (cat.equals("employment")) {
                        c.empGoalsTotal++;
                        if (done) {
                            c.empGoalsDone++;
                        }
                    }
                    if (!done && target != null
                            && target.toLocalDate().isBefore(today)) {
                        c.overdueGoals++;
                    }
                }
            }
        }

        // applications (table may not exist yet: then everything stays 0)
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT UPPER(NVL(o.OPPORTUNITY_TYPE, ' ')), UPPER(a.STATUS) "
                + "FROM OPPORTUNITY_APPLICATIONS a "
                + "LEFT JOIN OPPORTUNITIES o ON o.OPPORTUNITY_ID = a.OPPORTUNITY_ID "
                + "WHERE a.PRISONER_ID = ?")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean job = nz(rs.getString(1)).contains("JOB");
                    boolean done = "COMPLETED".equals(rs.getString(2));
                    if (job) {
                        c.jobApplied++;
                        if (done) {
                            c.jobDone++;
                        }
                    } else {
                        c.trainingApplied++;
                        if (done) {
                            c.trainingDone++;
                        }
                    }
                }
            }
        } catch (SQLException ignored) {
            // no applications table yet
        }

        // oldest pending support request
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT REQUEST_ID, TRUNC(SYSDATE) - TRUNC(REQUEST_DATE) FROM "
                + "(SELECT REQUEST_ID, REQUEST_DATE FROM SUPPORT_REQUESTS "
                + "WHERE PRISONER_ID = ? AND UPPER(STATUS) = 'PENDING' "
                + "ORDER BY REQUEST_DATE) WHERE ROWNUM = 1")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    c.pendingReqId = rs.getInt(1);
                    c.pendingDays = rs.getInt(2);
                }
            }
        }

        // snapshot at least 6 days old
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM (SELECT SNAP_DATE, P1, P2, P3, P4, P5, P6, READINESS "
                + "FROM PILLAR_SNAPSHOTS WHERE PRISONER_ID = ? "
                + "AND SNAP_DATE <= TRUNC(SYSDATE) - 6 ORDER BY SNAP_DATE DESC) "
                + "WHERE ROWNUM = 1")) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    c.prevDate = rs.getDate(1).toLocalDate();
                    c.prev = new double[6];
                    for (int i = 0; i < 6; i++) {
                        c.prev[i] = rs.getDouble(2 + i);
                    }
                    c.prevIndex = rs.getDouble(8);
                }
            }
        }

        score(c);
        return c;
    }

    /** Pure scoring: raw inputs in, six scores + explanation lines out. */
    public static void score(Compass c) {

        c.lines.clear();
        for (int i = 0; i < 6; i++) {
            c.lines.add(new ArrayList<>());
        }

        // 0 Documents: 4 documents x 25
        boolean anyDoc = false;
        for (String type : DOC_TYPES) {
            String st = c.docs.get(type);
            double pts = 0;
            String note = "Not recorded";
            if (st != null) {
                anyDoc = true;
                note = st;
                if (st.equalsIgnoreCase("Held")) {
                    pts = 25;
                } else if (st.equalsIgnoreCase("Applied")) {
                    pts = 12.5;
                }
            }
            c.lines.get(0).add(new Line(type, pts, 25, note));
        }
        c.assessed[0] = anyDoc;

        // 1 Skills & Education
        boolean edu = !c.education.trim().isEmpty()
            && !c.education.trim().toLowerCase().matches("none|nil|illiterate|no");
        c.lines.get(1).add(new Line("Education on record", edu ? 20 : 0, 20,
            edu ? c.education : "None recorded"));
        boolean sk = !c.skills.trim().isEmpty();
        c.lines.get(1).add(new Line("Skills on record", sk ? 20 : 0, 20,
            sk ? Theme.shorten(c.skills, 40) : "None recorded"));
        double train = c.trainingDone > 0 ? 30 : (c.trainingApplied > 0 ? 15 : 0);
        c.lines.get(1).add(new Line("Training / course", train, 30,
            c.trainingDone > 0 ? "Completed" : c.trainingApplied > 0
                ? "Applied, not completed" : "No application"));
        double eg = c.eduGoalsTotal == 0 ? 0 : 30.0 * c.eduGoalsDone / c.eduGoalsTotal;
        c.lines.get(1).add(new Line("Education / skills goals", eg, 30,
            c.eduGoalsTotal == 0 ? "No goals set"
                : c.eduGoalsDone + " of " + c.eduGoalsTotal + " done"));
        c.assessed[1] = true;

        // 2 Livelihood
        String emp = c.employment.trim().toLowerCase();
        boolean employed = emp.contains("employed") && !emp.contains("un");
        if (employed) {
            c.lines.get(2).add(new Line("Has a job or income", 100, 100, c.employment));
        } else {
            c.lines.get(2).add(new Line("Has a job or income", 0, 0, "Not employed"));
            c.lines.get(2).add(new Line("Job opportunity completed",
                c.jobDone > 0 ? 50 : 0, 50,
                c.jobDone > 0 ? "Yes" : "No"));
            c.lines.get(2).add(new Line("Applied for a job",
                c.jobApplied > 0 ? 30 : 0, 30,
                c.jobApplied > 0 ? c.jobApplied + " application(s)" : "No application"));
            double empg = c.empGoalsTotal == 0 ? 0
                : 20.0 * c.empGoalsDone / c.empGoalsTotal;
            c.lines.get(2).add(new Line("Employment goals", empg, 20,
                c.empGoalsTotal == 0 ? "No goals set"
                    : c.empGoalsDone + " of " + c.empGoalsTotal + " done"));
        }
        c.assessed[2] = true;

        // 3 Mind & Health
        boolean notRequired = c.counselling.trim().equalsIgnoreCase("No")
            && c.sessionsDone == 0;
        if (notRequired) {
            c.lines.get(3).add(new Line("Counselling not required", 60, 100,
                "Marked 'No' at registration"));
        } else {
            double sess = 40.0 * Math.min(1, c.sessionsDone / 4.0);
            c.lines.get(3).add(new Line("Sessions completed", sess, 40,
                c.sessionsDone + " (full marks at 4)"));
            double rate = (c.sessionsDone > 0 && c.avgRating > 0)
                ? 40.0 * (c.avgRating - 1) / 4.0 : 0;
            c.lines.get(3).add(new Line("Average progress rating", rate, 40,
                c.sessionsDone > 0 && c.avgRating > 0
                    ? String.format("%.1f of 5", c.avgRating) : "No rating yet"));
            double rec = 0;
            String recNote = "No session yet";
            if (c.lastSessionDays >= 0) {
                rec = c.lastSessionDays <= 14 ? 20 : (c.lastSessionDays <= 30 ? 10 : 0);
                recNote = c.lastSessionDays + " days ago";
            }
            c.lines.get(3).add(new Line("Recent session", rec, 20, recNote));
        }
        c.assessed[3] = true;

        // 4 Family & Home
        double h = c.housing.equals("Stable") ? 50 : c.housing.equals("Temporary") ? 25 : 0;
        double f = c.family.equals("Regular") ? 50 : c.family.equals("Occasional") ? 25 : 0;
        c.lines.get(4).add(new Line("Place to stay", h, 50, c.housing));
        c.lines.get(4).add(new Line("Family contact", f, 50, c.family));
        c.assessed[4] = !c.housing.equals("Not recorded") || !c.family.equals("Not recorded");

        // 5 Legal
        double cs = c.caseStatus.equals("Cleared") ? 50 : c.caseStatus.equals("Pending") ? 20 : 0;
        double pr = (c.parole.equals("Met") || c.parole.equals("Not applicable")) ? 50
            : c.parole.equals("At risk") ? 20 : 0;
        c.lines.get(5).add(new Line("Cases", cs, 50, c.caseStatus));
        c.lines.get(5).add(new Line("Parole conditions", pr, 50, c.parole));
        c.assessed[5] = !c.caseStatus.equals("Not recorded")
            || !c.parole.equals("Not recorded");

        // totals
        double idx = 0;
        for (int i = 0; i < 6; i++) {
            double s = 0;
            double max = 0;
            for (Line l : c.lines.get(i)) {
                s += l.pts;
                max += l.max;
            }
            if (i == 2 && employed) {
                s = 100;
            } else if (i == 3 && notRequired) {
                s = 60;
            } else if (max > 0) {
                s = Math.min(100, s * 100.0 / max);
            }
            c.score[i] = Math.round(s * 10) / 10.0;
            idx += c.score[i] * WEIGHTS[i] / 100.0;
        }
        c.index = Math.round(idx * 10) / 10.0;
    }

    public static int weakestOf(double[] s) {
        int w = 0;
        for (int i = 1; i < s.length; i++) {
            if (s[i] < s[w] || (s[i] == s[w] && WEIGHTS[i] > WEIGHTS[w])) {
                w = i;
            }
        }
        return w;
    }

    public static int band(double index) {
        return index < 35 ? 0 : index < 60 ? 1 : index < 80 ? 2 : 3;
    }

    public static final String[] BANDS = {
        "Needs urgent support", "Building foundations", "On track", "Thriving"
    };

    public static final String[] STAGES = {
        "Seed", "Sprout", "Sapling", "Young tree", "Strong tree", "Banyan"
    };

    public static int stage(double index) {
        return Math.min(5, (int) (index / 100.0 * 6));
    }

    /** Stores today's scores (one row per person per day). */
    public static void snapshot(Connection con, Compass c) {
        try {
            int n = 0;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM PILLAR_SNAPSHOTS WHERE PRISONER_ID = ? "
                    + "AND SNAP_DATE = TRUNC(SYSDATE) AND IS_DEMO = 0")) {
                ps.setInt(1, c.prisonerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    n = rs.getInt(1);
                }
            }
            if (n > 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE PILLAR_SNAPSHOTS SET P1=?, P2=?, P3=?, P4=?, P5=?, "
                        + "P6=?, READINESS=? WHERE PRISONER_ID = ? "
                        + "AND SNAP_DATE = TRUNC(SYSDATE) AND IS_DEMO = 0")) {
                    fill(ps, c.score, c.index, 1);
                    ps.setInt(8, c.prisonerId);
                    ps.executeUpdate();
                }
            } else {
                int id = CounsellorUtil.nextId(con, "PILLAR_SNAPSHOTS", "SNAP_ID");
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO PILLAR_SNAPSHOTS (P1, P2, P3, P4, P5, P6, "
                        + "READINESS, SNAP_ID, PRISONER_ID, SNAP_DATE, IS_DEMO) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUNC(SYSDATE), 0)")) {
                    fill(ps, c.score, c.index, 1);
                    ps.setInt(8, id);
                    ps.setInt(9, c.prisonerId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
            // history is a bonus: never block the screen
        }
    }

    private static void fill(PreparedStatement ps, double[] s, double idx, int from)
            throws SQLException {
        for (int i = 0; i < 6; i++) {
            ps.setDouble(from + i, s[i]);
        }
        ps.setDouble(from + 6, idx);
    }

    // ------------------------------------------------------------------
    // 2. EARLY WARNING  (plain rules, every flag explains itself)
    // ------------------------------------------------------------------
    public static List<Integer> prisonerIds(Connection con) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT PRISONER_ID FROM PRISONER_DETAILS ORDER BY PRISONER_ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    public static Alert evaluate(Compass c) {

        Alert a = new Alert();
        a.compass = c;
        int p = 0;
        List<String> todo = new ArrayList<>();

        if (c.pendingReqId > 0 && c.pendingDays >= 3) {
            int w = c.pendingDays >= 7 ? 3 : 1;
            p += w;
            a.reasons.add("Support request #" + c.pendingReqId
                + " unanswered for " + c.pendingDays + " days  (+" + w + ")");
            if (w == 3) {
                todo.add("answer support request #" + c.pendingReqId);
            }
        }

        boolean needsCounselling = !c.counselling.trim().equalsIgnoreCase("No");
        if (needsCounselling) {
            long gap = c.lastSessionDays >= 0 ? c.lastSessionDays : c.daysSinceRelease;
            if (gap > 14) {
                int w = gap > 30 ? 2 : 1;
                p += w;
                a.reasons.add(c.lastSessionDays >= 0
                    ? "No completed counselling session for " + gap + " days  (+" + w + ")"
                    : "No counselling session since release (" + gap + " days)  (+" + w + ")");
                todo.add("schedule a counselling session");
            }
        }

        if (c.overdueGoals > 0) {
            int w = c.overdueGoals >= 3 ? 2 : 1;
            p += w;
            a.reasons.add(c.overdueGoals + " rehabilitation goal(s) past the target date  (+" + w + ")");
            todo.add("review overdue goals");
        }

        if (c.prev != null) {
            double drop = c.prevIndex - c.index;
            if (drop >= 5) {
                p += 2;
                a.reasons.add(String.format(
                    "Compass shrinking: readiness %.0f to %.0f since %s  (+2)",
                    c.prevIndex, c.index, c.prevDate));
            }
            int worst = -1;
            double worstDrop = 0;
            for (int i = 0; i < 6; i++) {
                double d = c.prev[i] - c.score[i];
                if (d >= 10 && d > worstDrop) {
                    worstDrop = d;
                    worst = i;
                }
            }
            if (worst >= 0) {
                p += 1;
                a.reasons.add(String.format("%s pillar dropped %.0f to %.0f  (+1)",
                    PILLARS[worst], c.prev[worst], c.score[worst]));
                todo.add("check the " + PILLARS[worst] + " pillar");
            }
        }

        if (c.lastRating > 0 && c.lastRating <= 2) {
            p += 1;
            a.reasons.add("Last session was rated " + c.lastRating + "/5, needs attention  (+1)");
        }

        String emp = c.employment.trim().toLowerCase();
        boolean employed = emp.contains("employed") && !emp.contains("un");
        if (!employed && c.daysSinceRelease > 30) {
            p += 1;
            a.reasons.add("No income source " + c.daysSinceRelease
                + " days after release  (+1)");
            todo.add("link to a job or livelihood scheme");
        }

        if (c.index < 35) {
            p += 1;
            a.reasons.add(String.format("Readiness index is very low (%.0f)  (+1)", c.index));
        }

        a.points = p;
        a.level = p >= 5 ? "HIGH" : p >= 3 ? "MEDIUM" : p >= 1 ? "WATCH" : "OK";
        a.suggestion = todo.isEmpty() ? "No action needed right now."
            : "Call about: " + String.join(", ", todo) + ".";
        return a;
    }

    /** All people with at least one flag, worst first. */
    public static List<Alert> earlyWarning(Connection con, boolean first90Days)
            throws SQLException {
        List<Alert> out = new ArrayList<>();
        for (Alert a : earlyWarningAll(con, first90Days)) {
            if (a.points > 0) {
                out.add(a);
            }
        }
        return out;
    }

    /** Everyone in the watch window (flagged or not), worst first. */
    public static List<Alert> earlyWarningAll(Connection con, boolean first90Days)
            throws SQLException {

        List<Alert> out = new ArrayList<>();
        for (int id : prisonerIds(con)) {
            Compass c = compute(con, id);
            snapshot(con, c);
            if (first90Days && (c.release == null || c.daysSinceRelease < 0
                    || c.daysSinceRelease > 90)) {
                continue;
            }
            out.add(evaluate(c));
        }
        out.sort((x, y) -> {
            if (x.points != y.points) {
                return y.points - x.points;
            }
            return Double.compare(x.compass.index, y.compass.index);
        });
        return out;
    }

    // ------------------------------------------------------------------
    // 3. SCHEME MATCHER
    // ------------------------------------------------------------------
    private static final String[][] KEYWORDS = {
        {"aadhaar", "aadhar", "document", "bank account", "jan dhan",
         "identity", "id proof", "ration card", "voter"},
        {"skill", "training", "education", "scholarship", "vocational",
         "course", "literacy"},
        {"employment", "job", "loan", "livelihood", "self-help", "self help",
         "entrepreneur", "mudra", "wage", "enterprise", "income", "startup"},
        {"health", "counsel", "de-addiction", "deaddiction", "mental",
         "medical", "wellbeing"},
        {"housing", "shelter", "home", "family", "pension", "widow", "child"},
        {"legal", "bail", "court", "parole", "remission", "lawyer"}
    };

    public static int pillarFromTag(String tag) {
        if (tag == null) {
            return -2;
        }
        for (int i = 0; i < 6; i++) {
            if (PILLARS[i].equalsIgnoreCase(tag.trim())) {
                return i;
            }
        }
        return tag.trim().equalsIgnoreCase("General") ? -1 : -2;
    }

    public static int guessPillar(String text) {
        String t = nz(text).toLowerCase();
        int best = -1;
        int bestHits = 0;
        for (int i = 0; i < 6; i++) {
            int hits = 0;
            for (String k : KEYWORDS[i]) {
                if (t.contains(k)) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = i;
            }
        }
        return best;
    }

    public static List<SchemeInfo> activeSchemes(Connection con) throws SQLException {

        ensure(con);
        List<SchemeInfo> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SCHEME_ID, SCHEME_NAME, SCHEME_TYPE, DEPARTMENT_NAME, "
                + "BENEFITS, PILLAR, DESCRIPTION FROM GOVERNMENT_SCHEMES "
                + "WHERE UPPER(TRIM(STATUS)) = 'ACTIVE' ORDER BY SCHEME_ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SchemeInfo s = new SchemeInfo();
                s.id = rs.getInt(1);
                s.name = nz(rs.getString(2));
                s.type = nz(rs.getString(3));
                s.dept = nz(rs.getString(4));
                s.benefits = nz(rs.getString(5));
                String tag = rs.getString(6);
                int p = pillarFromTag(tag);
                if (p == -2) {
                    s.pillar = guessPillar(s.name + " " + s.type + " " + s.benefits
                        + " " + nz(rs.getString(7)));
                    s.auto = true;
                } else {
                    s.pillar = p;
                }
                list.add(s);
            }
        }
        return list;
    }

    public static Match match(List<SchemeInfo> schemes, Compass c, int max) {

        Match m = new Match();
        m.weakest = c.weakest();

        Integer[] order = {0, 1, 2, 3, 4, 5};
        Arrays.sort(order, (x, y) -> {
            int d = Double.compare(c.score[x], c.score[y]);
            return d != 0 ? d : WEIGHTS[y] - WEIGHTS[x];
        });

        for (int pillar : order) {
            for (SchemeInfo s : schemes) {
                if (s.pillar == pillar && m.schemes.size() < max) {
                    m.schemes.add(s);
                }
            }
            if (!m.schemes.isEmpty()) {
                m.pillar = pillar;
                break;
            }
        }
        m.fallback = m.pillar != m.weakest;
        return m;
    }

    public static String getSchemePillar(int schemeId) {
        try (Connection con = DBConnection.getConnection()) {
            ensure(con);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT PILLAR FROM GOVERNMENT_SCHEMES WHERE SCHEME_ID = ?")) {
                ps.setInt(1, schemeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
        } catch (SQLException ignored) {
            // treated as "Auto"
        }
        return null;
    }

    /** tag = null for Auto. */
    public static void setSchemePillar(int schemeId, String tag) {
        try (Connection con = DBConnection.getConnection()) {
            ensure(con);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE GOVERNMENT_SCHEMES SET PILLAR = ? WHERE SCHEME_ID = ?")) {
                ps.setString(1, tag);
                ps.setInt(2, schemeId);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            CounsellorUtil.error(null, "Could not save the pillar tag:\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 4. COMMAND CENTRE  (anonymised: no names leave this method)
    // ------------------------------------------------------------------
    private static final String[] ACTION_TEXT = {
        "Documents are the biggest gap: consider a document camp (ID, bank account, address proof).",
        "Skills and education are the biggest gap: expand training seats and bridge courses.",
        "Livelihood is the biggest gap: run a job fair or link NGOs for placements.",
        "Mind and health are the biggest gap: add counselling capacity and follow-up visits.",
        "Family and home are the biggest gap: strengthen shelter access and family mediation.",
        "Legal is the biggest gap: organise legal-aid clinics for pending cases and parole."
    };

    public static Stats stats(Connection con, int minGroup) throws SQLException {

        ensure(con);
        Stats s = new Stats();

        double sum = 0;
        double[] ps = new double[6];
        for (int id : prisonerIds(con)) {
            Compass c = compute(con, id);
            snapshot(con, c);
            s.people.add(c);
            sum += c.index;
            for (int i = 0; i < 6; i++) {
                ps[i] += c.score[i];
            }
            s.bands[band(c.index)]++;
            s.weakestCount[c.weakest()]++;
            Alert a = evaluate(c);
            if (a.level.equals("HIGH")) {
                s.high++;
            } else if (a.level.equals("MEDIUM")) {
                s.medium++;
            } else if (a.level.equals("WATCH")) {
                s.watch++;
            }
        }

        int n = s.people.size();
        if (n > 0) {
            s.avg = sum / n;
            for (int i = 0; i < 6; i++) {
                s.pillarAvg[i] = ps[i] / n;
            }
            s.weakest = weakestOf(s.pillarAvg);
        }

        // districts
        Map<String, List<Compass>> by = new LinkedHashMap<>();
        for (String d : DISTRICTS) {
            by.put(d, new ArrayList<>());
        }
        for (Compass c : s.people) {
            if (by.containsKey(c.district)) {
                by.get(c.district).add(c);
            } else {
                s.unassigned++;
            }
        }
        for (Map.Entry<String, List<Compass>> e : by.entrySet()) {
            District d = new District();
            d.name = e.getKey();
            d.n = e.getValue().size();
            if (d.n > 0) {
                double t = 0;
                for (Compass c : e.getValue()) {
                    t += c.index;
                    for (int i = 0; i < 6; i++) {
                        d.pillarAvg[i] += c.score[i];
                    }
                }
                d.avg = t / d.n;
                for (int i = 0; i < 6; i++) {
                    d.pillarAvg[i] /= d.n;
                }
            }
            d.hidden = d.n > 0 && d.n < minGroup;
            s.districts.add(d);
        }

        s.centres = centreStats(con);

        s.pendingOver7 = safeInt(con,
            "SELECT COUNT(*) FROM SUPPORT_REQUESTS WHERE UPPER(STATUS) = 'PENDING' "
            + "AND TRUNC(SYSDATE) - TRUNC(REQUEST_DATE) > 7");

        List<SchemeInfo> schemes = activeSchemes(con);
        s.activeSchemes = schemes.size();
        for (SchemeInfo sc : schemes) {
            if (sc.pillar >= 0) {
                s.schemesPerPillar[sc.pillar]++;
            }
        }

        // plain-language actions
        if (n == 0) {
            s.actions.add("No prisoners are registered yet, so there is nothing to analyse.");
            return s;
        }
        s.actions.add(ACTION_TEXT[s.weakest]);
        if (s.pendingOver7 > 0) {
            s.actions.add(s.pendingOver7 + " support request(s) have waited more than 7 days: "
                + "assign an owner and clear them this week.");
        }
        if (s.high > 0) {
            s.actions.add(s.high + " person(s) are flagged HIGH: counsellors should call them today.");
        }
        for (int i = 0; i < 6; i++) {
            if (s.weakestCount[i] > 0 && s.schemesPerPillar[i] == 0) {
                s.actions.add("No active scheme is tagged for " + PILLARS[i] + ", yet "
                    + s.weakestCount[i] + " person(s) are weakest there: add or tag a scheme.");
                break;
            }
        }
        District low = null;
        for (District d : s.districts) {
            if (d.n >= Math.max(1, minGroup) && (low == null || d.avg < low.avg)) {
                low = d;
            }
        }
        if (low != null) {
            s.actions.add(String.format("%s has the lowest average readiness (%.0f): "
                + "prioritise it for the next camp.", low.name, low.avg));
        }
        return s;
    }

    public static int uncoveredGaps(Stats s) {
        int k = 0;
        for (int i = 0; i < 6; i++) {
            if (s.weakestCount[i] > 0 && s.schemesPerPillar[i] == 0) {
                k++;
            }
        }
        return k;
    }

    // ------------------------------------------------------------------
    // Checklist (documents, housing, family, legal) - counsellor input
    // ------------------------------------------------------------------
    public static void saveChecklist(Connection con, int pid, Map<String, String> docs,
            String housing, String family, String caseStatus, String parole,
            String district) throws SQLException {

        ensure(con);
        for (Map.Entry<String, String> e : docs.entrySet()) {
            int n = 0;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE DOCUMENT_STATUS SET STATUS = ?, UPDATED_DATE = SYSDATE, "
                    + "IS_DEMO = 0 WHERE PRISONER_ID = ? AND DOC_TYPE = ?")) {
                ps.setString(1, e.getValue());
                ps.setInt(2, pid);
                ps.setString(3, e.getKey());
                n = ps.executeUpdate();
            }
            if (n == 0) {
                int id = CounsellorUtil.nextId(con, "DOCUMENT_STATUS", "DOC_ID");
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO DOCUMENT_STATUS (DOC_ID, PRISONER_ID, DOC_TYPE, "
                        + "STATUS) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, id);
                    ps.setInt(2, pid);
                    ps.setString(3, e.getKey());
                    ps.setString(4, e.getValue());
                    ps.executeUpdate();
                }
            }
        }

        int n;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE REINTEGRATION_STATUS SET HOUSING = ?, FAMILY_CONTACT = ?, "
                + "CASE_STATUS = ?, PAROLE_STATUS = ?, UPDATED_DATE = SYSDATE, "
                + "IS_DEMO = 0 WHERE PRISONER_ID = ?")) {
            ps.setString(1, housing);
            ps.setString(2, family);
            ps.setString(3, caseStatus);
            ps.setString(4, parole);
            ps.setInt(5, pid);
            n = ps.executeUpdate();
        }
        if (n == 0) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO REINTEGRATION_STATUS (PRISONER_ID, HOUSING, "
                    + "FAMILY_CONTACT, CASE_STATUS, PAROLE_STATUS) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, pid);
                ps.setString(2, housing);
                ps.setString(3, family);
                ps.setString(4, caseStatus);
                ps.setString(5, parole);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE PRISONER_DETAILS SET DISTRICT = ? WHERE PRISONER_ID = ?")) {
            ps.setString(1, district == null || district.isEmpty() ? null : district);
            ps.setInt(2, pid);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Demo data (clearly marked IS_DEMO = 1, removable in one click)
    // ------------------------------------------------------------------
    public static int seedDemo(Connection con) throws SQLException {

        ensure(con);
        seedCentres(con);
        List<Integer> ids = prisonerIds(con);
        int seeded = 0;
        int k = 0;

        for (int pid : ids) {
            Random r = new Random(pid * 7919L + 13);

            // district (only if empty)
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE PRISONER_DETAILS SET DISTRICT = ? "
                    + "WHERE PRISONER_ID = ? AND DISTRICT IS NULL")) {
                ps.setString(1, DEMO_DISTRICTS[k % DEMO_DISTRICTS.length]);
                ps.setInt(2, pid);
                ps.executeUpdate();
            }
            k++;

            // checklist (only for people with nothing recorded)
            int has = safeInt(con,
                "SELECT COUNT(*) FROM REINTEGRATION_STATUS WHERE PRISONER_ID = ?", pid);
            if (has == 0) {
                for (String type : DOC_TYPES) {
                    int roll = r.nextInt(10);
                    String st = roll < 4 ? "Held" : roll < 7 ? "Applied" : "Missing";
                    int id = CounsellorUtil.nextId(con, "DOCUMENT_STATUS", "DOC_ID");
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO DOCUMENT_STATUS (DOC_ID, PRISONER_ID, DOC_TYPE, "
                            + "STATUS, IS_DEMO) VALUES (?, ?, ?, ?, 1)")) {
                        ps.setInt(1, id);
                        ps.setInt(2, pid);
                        ps.setString(3, type);
                        ps.setString(4, st);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO REINTEGRATION_STATUS (PRISONER_ID, HOUSING, "
                        + "FAMILY_CONTACT, CASE_STATUS, PAROLE_STATUS, IS_DEMO) "
                        + "VALUES (?, ?, ?, ?, ?, 1)")) {
                    ps.setInt(1, pid);
                    ps.setString(2, new String[]{"None", "Temporary", "Stable"}[r.nextInt(3)]);
                    ps.setString(3, new String[]{"None", "Occasional", "Regular"}[r.nextInt(3)]);
                    ps.setString(4, new String[]{"Pending", "Cleared", "Cleared"}[r.nextInt(3)]);
                    ps.setString(5, new String[]{"At risk", "Met", "Met", "Not applicable"}[r.nextInt(4)]);
                    ps.executeUpdate();
                }
                seeded++;
            }

            // four weekly history rows, so the "shrinking compass" has a past
            Compass c = compute(con, pid);
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM PILLAR_SNAPSHOTS WHERE PRISONER_ID = ? AND IS_DEMO = 1")) {
                ps.setInt(1, pid);
                ps.executeUpdate();
            }
            boolean slipping = pid % 3 == 0;
            for (int w = 1; w <= 4; w++) {
                double[] s = new double[6];
                double idx = 0;
                for (int i = 0; i < 6; i++) {
                    double drift = slipping ? 4.5 * w : -2.0 * w;
                    s[i] = Math.max(0, Math.min(100,
                        c.score[i] + drift + (r.nextDouble() - 0.5) * 6));
                    idx += s[i] * WEIGHTS[i] / 100.0;
                }
                int id = CounsellorUtil.nextId(con, "PILLAR_SNAPSHOTS", "SNAP_ID");
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO PILLAR_SNAPSHOTS (P1, P2, P3, P4, P5, P6, READINESS, "
                        + "SNAP_ID, PRISONER_ID, SNAP_DATE, IS_DEMO) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUNC(SYSDATE) - ?, 1)")) {
                    fill(ps, s, idx, 1);
                    ps.setInt(8, id);
                    ps.setInt(9, pid);
                    ps.setInt(10, 7 * w);
                    ps.executeUpdate();
                }
            }
        }
        return seeded;
    }

    public static void clearDemo(Connection con) throws SQLException {
        ensure(con);
        for (String t : new String[]{"DOCUMENT_STATUS", "REINTEGRATION_STATUS",
                "PILLAR_SNAPSHOTS", "CENTRES"}) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("DELETE FROM " + t + " WHERE IS_DEMO = 1");
            }
        }
    }

    // ------------------------------------------------------------------
    // Centres (rehabilitation centres, NGOs, training partners) for the map
    // ------------------------------------------------------------------
    public static final String[] CENTRE_TYPES = {
        "Rehabilitation Centre", "NGO Partner", "Training Partner"
    };

    /** lon, lat of each entry of DISTRICTS (the last one, "Other", has none). */
    public static final double[][] DISTRICT_LL = {
        {80.27, 13.08}, {76.96, 11.00}, {78.12, 9.92}, {78.70, 10.80},
        {78.15, 11.65}, {77.70, 8.73}, {77.72, 11.34}, {79.13, 12.92},
        {78.13, 8.76}, {79.14, 10.78}, {77.98, 10.36}, {77.55, 8.18},
        {79.77, 11.75}, {77.96, 9.58}, {0, 0}
    };

    public static class CentreStats {
        public final int[][] byDistrict = new int[DISTRICTS.length][3];
        public final int[] totals = new int[3];
        public int districtsCovered;
        public int unassigned;
    }

    public static CentreStats centreStats(Connection con) throws SQLException {

        ensure(con);
        CentreStats cs = new CentreStats();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT NVL(DISTRICT, ' '), CENTRE_TYPE, COUNT(*) FROM CENTRES "
                + "GROUP BY DISTRICT, CENTRE_TYPE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String d = rs.getString(1).trim();
                String t = nz(rs.getString(2));
                int n = rs.getInt(3);
                int ti = -1;
                for (int i = 0; i < CENTRE_TYPES.length; i++) {
                    if (CENTRE_TYPES[i].equalsIgnoreCase(t)) {
                        ti = i;
                    }
                }
                if (ti < 0) {
                    continue;
                }
                cs.totals[ti] += n;
                int di = -1;
                for (int i = 0; i < DISTRICTS.length; i++) {
                    if (DISTRICTS[i].equalsIgnoreCase(d)) {
                        di = i;
                    }
                }
                if (di < 0) {
                    cs.unassigned += n;
                } else {
                    cs.byDistrict[di][ti] += n;
                }
            }
        }
        for (int[] row : cs.byDistrict) {
            if (row[0] + row[1] + row[2] > 0) {
                cs.districtsCovered++;
            }
        }
        return cs;
    }

    public static List<Object[]> listCentres(Connection con) throws SQLException {
        ensure(con);
        List<Object[]> rows = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CENTRE_ID, CENTRE_NAME, CENTRE_TYPE, DISTRICT, IS_DEMO "
                + "FROM CENTRES ORDER BY DISTRICT, CENTRE_TYPE, CENTRE_NAME");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                    nz(rs.getString(4)), rs.getInt(5) == 1 ? "demo" : ""});
            }
        }
        return rows;
    }

    public static void addCentre(Connection con, String name, String type, String district)
            throws SQLException {
        ensure(con);
        int id = CounsellorUtil.nextId(con, "CENTRES", "CENTRE_ID");
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO CENTRES (CENTRE_ID, CENTRE_NAME, CENTRE_TYPE, DISTRICT) "
                + "VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setString(4, district);
            ps.executeUpdate();
        }
    }

    public static void deleteCentre(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM CENTRES WHERE CENTRE_ID = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static void seedCentres(Connection con) throws SQLException {
        if (safeInt(con, "SELECT COUNT(*) FROM CENTRES") > 0) {
            return;
        }
        for (int i = 0; i < DISTRICTS.length - 1; i++) {
            Random r = new Random(i * 31L + 5);
            int[] n = {r.nextInt(3), 1 + r.nextInt(4), r.nextInt(3)};
            for (int t = 0; t < 3; t++) {
                for (int k = 1; k <= n[t]; k++) {
                    int id = CounsellorUtil.nextId(con, "CENTRES", "CENTRE_ID");
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO CENTRES (CENTRE_ID, CENTRE_NAME, CENTRE_TYPE, "
                            + "DISTRICT, IS_DEMO) VALUES (?, ?, ?, ?, 1)")) {
                        ps.setInt(1, id);
                        ps.setString(2, "Demo " + CENTRE_TYPES[t] + " " + k + " - " + DISTRICTS[i]);
                        ps.setString(3, CENTRE_TYPES[t]);
                        ps.setString(4, DISTRICTS[i]);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s) {
        return s == null || s.trim().isEmpty() ? "Not recorded" : s.trim();
    }

    private static int safeInt(Connection con, String sql, Object... params) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }
}
