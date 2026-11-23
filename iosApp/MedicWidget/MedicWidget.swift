import SwiftUI
import WidgetKit

/// The app's numbers, written by the shared Kotlin code into the App Group (see WidgetBridge).
struct StudySnapshot {
    var streak = 0
    var doneToday = false
    var todayCount = 0
    var goal = 10
    var reviewDue = 0
    var ready = false

    static let group = "group.com.taninoni.medic"

    /// Local epoch day, the same count the app uses.
    static func localDay(_ date: Date = Date()) -> Int {
        let offset = TimeZone.current.secondsFromGMT(for: date)
        return Int(floor((date.timeIntervalSince1970 + Double(offset)) / 86_400))
    }

    static func load(at date: Date = Date()) -> StudySnapshot {
        guard let d = UserDefaults(suiteName: group), d.bool(forKey: "ready") else { return StudySnapshot() }
        var s = StudySnapshot(
            streak: d.integer(forKey: "streak"), doneToday: d.bool(forKey: "doneToday"),
            todayCount: d.integer(forKey: "todayCount"), goal: max(1, d.integer(forKey: "goal")),
            reviewDue: d.integer(forKey: "reviewDue"), ready: true
        )
        let written = d.integer(forKey: "day")
        let today = localDay(date)
        if today > written {
            // A new day began since the app last wrote: nothing done yet today. Streak freezes (up to 2)
            // can bridge missed days, which only the app knows, so the streak is dropped only past that.
            if today - written > 3 { s.streak = 0 }
            s.todayCount = 0
            s.doneToday = false
        }
        return s
    }

    var left: Int { max(0, goal - todayCount) }
    var progress: Double { min(1, Double(todayCount) / Double(goal)) }
}

struct Entry: TimelineEntry {
    let date: Date
    let snap: StudySnapshot
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry {
        Entry(date: Date(), snap: StudySnapshot(streak: 12, todayCount: 6, goal: 10, reviewDue: 8, ready: true))
    }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : Entry(date: Date(), snap: .load()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        // One entry now and one just after midnight, when the day's count starts over.
        let now = Date()
        let cal = Calendar.current
        let midnight = cal.startOfDay(for: cal.date(byAdding: .day, value: 1, to: now)!).addingTimeInterval(5)
        let evening = cal.date(bySettingHour: 20, minute: 0, second: 0, of: now) ?? now
        var entries = [Entry(date: now, snap: .load(at: now))]
        if evening > now { entries.append(Entry(date: evening, snap: .load(at: evening))) }
        entries.append(Entry(date: midnight, snap: .load(at: midnight)))
        completion(Timeline(entries: entries, policy: .after(midnight)))
    }
}

private let brand = Color(red: 0xF2 / 255, green: 0x69 / 255, blue: 0x22 / 255)
private let brandDeep = Color(red: 0xD9 / 255, green: 0x55 / 255, blue: 0x12 / 255)

/// The streak flame, filled once today's goal is met.
struct Flame: View {
    var lit: Bool
    var size: CGFloat
    var body: some View {
        Image(systemName: lit ? "flame.fill" : "flame")
            .font(.system(size: size, weight: .bold))
            .foregroundStyle(.white.opacity(lit ? 1 : 0.85))
    }
}

struct ProgressBar: View {
    var value: Double
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.28))
                Capsule().fill(.white).frame(width: max(6, g.size.width * value))
            }
        }
        .frame(height: 6)
    }
}

/// "5 שאלות עכשיו": opens the app straight into five quick questions.
struct QuickButton: View {
    var compact = false
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "bolt.fill").font(.system(size: compact ? 11 : 12, weight: .heavy))
            Text("5 שאלות עכשיו").font(.system(size: compact ? 12.5 : 13.5, weight: .heavy))
                .lineLimit(1).minimumScaleFactor(0.8).fixedSize(horizontal: !compact, vertical: false)
        }
        .foregroundStyle(brand)
        .padding(.horizontal, compact ? 10 : 14)
        .frame(height: compact ? 30 : 34)
        .frame(maxWidth: compact ? .infinity : nil)
        .background(Capsule().fill(.white))
    }
}

struct SmallView: View {
    let s: StudySnapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 6) {
                Flame(lit: s.doneToday, size: 20)
                Spacer(minLength: 0)
                Text(s.doneToday ? "הושלם" : "\(s.todayCount)/\(s.goal)")
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundStyle(.white.opacity(0.9))
            }
            Spacer(minLength: 4)
            Text("\(s.streak)")
                .font(.system(size: 40, weight: .black, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.6)
            Text(s.streak == 1 ? "יום ברצף" : "ימים ברצף")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.white.opacity(0.9))
            Spacer(minLength: 8)
            QuickButton(compact: true)
        }
    }
}

struct MediumView: View {
    let s: StudySnapshot
    var line: String {
        if s.doneToday { return "היעד היומי הושלם 🎉" }
        if s.streak > 0 { return "עוד \(s.left) שאלות ושמרת על הרצף" }
        return "\(s.goal) שאלות ביום שומרות את החומר טרי"
    }
    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Flame(lit: s.doneToday, size: 22)
                Spacer(minLength: 2)
                Text("\(s.streak)")
                    .font(.system(size: 44, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.6)
                Text(s.streak == 1 ? "יום ברצף" : "ימים ברצף")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .frame(width: 84, alignment: .leading)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("היום").font(.system(size: 13, weight: .heavy)).foregroundStyle(.white.opacity(0.9))
                    Spacer()
                    Text("\(s.todayCount)/\(s.goal)").font(.system(size: 13, weight: .heavy)).foregroundStyle(.white)
                }
                ProgressBar(value: s.progress)
                Text(line)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    Link(destination: URL(string: "medic://quick")!) { QuickButton() }
                    if s.reviewDue > 0 {
                        Link(destination: URL(string: "medic://review")!) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.triangle.2.circlepath").font(.system(size: 11, weight: .heavy))
                                Text("\(s.reviewDue) לחזרה").font(.system(size: 13, weight: .heavy)).lineLimit(1).fixedSize()
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 9)
                            .frame(height: 34)
                            .background(Capsule().fill(.white.opacity(0.22)))
                        }
                    }
                }
            }
        }
    }
}

/// Lock screen: the streak in a ring that fills with today's progress.
struct CircularView: View {
    let s: StudySnapshot
    var body: some View {
        Gauge(value: s.progress) {
            Image(systemName: "flame.fill")
        } currentValueLabel: {
            Text("\(s.streak)").font(.system(size: 18, weight: .black, design: .rounded))
        }
        .gaugeStyle(.accessoryCircularCapacity)
    }
}

struct InlineView: View {
    let s: StudySnapshot
    var body: some View {
        Text(s.doneToday ? "🔥 \(s.streak) ימים · הושלם" : "🔥 \(s.streak) ימים · עוד \(s.left) שאלות")
    }
}

struct WidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: Entry

    var body: some View {
        let s = entry.snap
        Group {
            if !s.ready {
                VStack(spacing: 6) {
                    Image(systemName: "cross.case.fill").font(.system(size: 26, weight: .bold)).foregroundStyle(.white)
                    Text("פתחו את מסלול חובש").font(.system(size: 13, weight: .bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                }
            } else {
                switch family {
                case .systemMedium: MediumView(s: s)
                case .accessoryCircular: CircularView(s: s)
                case .accessoryInline: InlineView(s: s)
                default: SmallView(s: s)
                }
            }
        }
        .environment(\.layoutDirection, .rightToLeft)
        .widgetURL(URL(string: family == .systemMedium ? "medic://home" : "medic://quick"))
        .modifier(BrandBackground(family: family))
    }
}

/// Orange card on the home screen; the system's own look on the lock screen.
struct BrandBackground: ViewModifier {
    let family: WidgetFamily
    func body(content: Content) -> some View {
        let isAccessory = family == .accessoryCircular || family == .accessoryInline || family == .accessoryRectangular
        let fill = LinearGradient(colors: [brand, brandDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
        if #available(iOSApplicationExtension 17.0, *) {
            if isAccessory { content.containerBackground(.clear, for: .widget) }
            else { content.containerBackground(fill, for: .widget) }
        } else {
            if isAccessory { content }
            else { content.padding(16).frame(maxWidth: .infinity, maxHeight: .infinity).background(fill) }
        }
    }
}

struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StreakWidget", provider: Provider()) { entry in
            WidgetView(entry: entry)
        }
        .configurationDisplayName("רצף יומי")
        .description("הרצף שלך, ההתקדמות היומית וכפתור ל־5 שאלות מהירות.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryInline])
    }
}

@main
struct MedicWidgets: WidgetBundle {
    var body: some Widget { StreakWidget() }
}
