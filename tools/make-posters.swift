// Builds the six feature posters, the Play feature graphic and the Play icon from real emulator screenshots.
//   swift tools/make-posters.swift docs/play/screenshots-raw docs/play/graphics
// Posters are 1080x1920 (9:16: usable as Play phone screenshots and as Instagram stories), black and white like the app.
import Foundation
import CoreGraphics
import CoreText
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
let rawDir = args.count > 1 ? args[1] : "docs/play/screenshots-raw"
let outDir = args.count > 2 ? args[2] : "docs/play/graphics"
try? FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

func load(_ name: String) -> CGImage {
    let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: "\(rawDir)/\(name).png") as CFURL, nil)!
    return CGImageSourceCreateImageAtIndex(src, 0, nil)!
}

let white = CGColor(red: 1, green: 1, blue: 1, alpha: 1)
let grey = CGColor(red: 0.72, green: 0.72, blue: 0.72, alpha: 1)
let black = CGColor(red: 0, green: 0, blue: 0, alpha: 1)

/// A canvas with a top-left based API. The bitmap has no alpha, as Play wants for screenshots.
final class Canvas {
    let w: CGFloat, h: CGFloat
    let ctx: CGContext
    init(_ w: Int, _ h: Int) {
        self.w = CGFloat(w); self.h = CGFloat(h)
        ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        ctx.setFillColor(black)
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.interpolationQuality = .high
        ctx.setAllowsFontSmoothing(true)
    }

    func rect(_ x: CGFloat, _ top: CGFloat, _ width: CGFloat, _ height: CGFloat) -> CGRect { CGRect(x: x, y: h - top - height, width: width, height: height) }

    /// Centered, wrapped text in a box whose top is [top].
    func text(_ s: String, x: CGFloat, top: CGFloat, width: CGFloat, size: CGFloat, bold: Bool, color: CGColor, align: CTTextAlignment = .center, line: CGFloat = 1.12) {
        let font = CTFontCreateWithName((bold ? "HelveticaNeue-Bold" : "HelveticaNeue") as CFString, size, nil)
        var alignment = align
        var lineHeight = size * line
        let settings = [
            CTParagraphStyleSetting(spec: .alignment, valueSize: MemoryLayout<CTTextAlignment>.size, value: &alignment),
            CTParagraphStyleSetting(spec: .minimumLineHeight, valueSize: MemoryLayout<CGFloat>.size, value: &lineHeight),
            CTParagraphStyleSetting(spec: .maximumLineHeight, valueSize: MemoryLayout<CGFloat>.size, value: &lineHeight),
        ]
        let style = CTParagraphStyleCreate(settings, settings.count)
        let attrs: [CFString: Any] = [kCTFontAttributeName: font, kCTForegroundColorAttributeName: color, kCTParagraphStyleAttributeName: style]
        let str = CFAttributedStringCreate(nil, s as CFString, attrs as CFDictionary)!
        let setter = CTFramesetterCreateWithAttributedString(str)
        let fit = CTFramesetterSuggestFrameSizeWithConstraints(setter, CFRange(), nil, CGSize(width: width, height: 10_000), nil)
        let box = rect(x, top, width, fit.height)
        let frame = CTFramesetterCreateFrame(setter, CFRange(), CGPath(rect: box, transform: nil), nil)
        CTFrameDraw(frame, ctx)
    }

    func textHeight(_ s: String, width: CGFloat, size: CGFloat, bold: Bool, line: CGFloat = 1.12) -> CGFloat {
        let font = CTFontCreateWithName((bold ? "HelveticaNeue-Bold" : "HelveticaNeue") as CFString, size, nil)
        var lineHeight = size * line
        let settings = [
            CTParagraphStyleSetting(spec: .minimumLineHeight, valueSize: MemoryLayout<CGFloat>.size, value: &lineHeight),
            CTParagraphStyleSetting(spec: .maximumLineHeight, valueSize: MemoryLayout<CGFloat>.size, value: &lineHeight),
        ]
        let style = CTParagraphStyleCreate(settings, settings.count)
        let attrs: [CFString: Any] = [kCTFontAttributeName: font, kCTParagraphStyleAttributeName: style]
        let setter = CTFramesetterCreateWithAttributedString(CFAttributedStringCreate(nil, s as CFString, attrs as CFDictionary)!)
        return CTFramesetterSuggestFrameSizeWithConstraints(setter, CFRange(), nil, CGSize(width: width, height: 10_000), nil).height
    }

    /// The app's crescent: a white disc with a black disc cut out of it (the launcher icon's two circles, on a 108 grid).
    func crescent(centerX: CGFloat, top: CGFloat, size: CGFloat) {
        let k = size / 108
        let cx = centerX - 54 * k
        ctx.setFillColor(white)
        ctx.fillEllipse(in: rect(cx + 28 * k, top + 28 * k, 52 * k, 52 * k))
        ctx.setFillColor(black)
        ctx.fillEllipse(in: rect(cx + 42 * k, top + 24 * k, 44 * k, 44 * k))
    }

    /// A phone: [shot] cropped to [crop], [width] wide, its top at [top]. It may run off the bottom edge, which is clipped.
    func phone(_ shot: CGImage, crop: CGRect, x: CGFloat, top: CGFloat, width: CGFloat) {
        let height = width * crop.height / crop.width
        let bezel: CGFloat = max(8, width * 0.02)
        let outer = rect(x, top, width, height)
        let radius = width * 0.11
        ctx.saveGState()
        ctx.addPath(CGPath(roundedRect: outer, cornerWidth: radius, cornerHeight: radius, transform: nil))
        ctx.setFillColor(CGColor(red: 0.16, green: 0.16, blue: 0.16, alpha: 1))
        ctx.fillPath()
        ctx.addPath(CGPath(roundedRect: outer.insetBy(dx: 1, dy: 1), cornerWidth: radius, cornerHeight: radius, transform: nil))
        ctx.setStrokeColor(CGColor(red: 0.45, green: 0.45, blue: 0.45, alpha: 1))
        ctx.setLineWidth(3)
        ctx.strokePath()
        let inner = outer.insetBy(dx: bezel, dy: bezel)
        ctx.addPath(CGPath(roundedRect: inner, cornerWidth: radius - bezel, cornerHeight: radius - bezel, transform: nil))
        ctx.clip()
        ctx.draw(shot.cropping(to: crop)!, in: inner)
        ctx.restoreGState()
    }

    /// A rounded picture, for the notification card.
    func card(_ shot: CGImage, crop: CGRect, x: CGFloat, top: CGFloat, width: CGFloat, radius: CGFloat) {
        let height = width * crop.height / crop.width
        let r = rect(x, top, width, height)
        ctx.saveGState()
        ctx.addPath(CGPath(roundedRect: r, cornerWidth: radius, cornerHeight: radius, transform: nil))
        ctx.clip()
        ctx.draw(shot.cropping(to: crop)!, in: r)
        ctx.restoreGState()
    }

    /// A tick in a white circle, for the battery poster's rows.
    func tick(x: CGFloat, top: CGFloat, size: CGFloat) {
        ctx.setFillColor(white)
        ctx.fillEllipse(in: rect(x, top, size, size))
        ctx.setStrokeColor(black)
        ctx.setLineWidth(size * 0.11)
        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)
        let p = { (fx: CGFloat, fy: CGFloat) -> CGPoint in CGPoint(x: x + fx * size, y: self.h - (top + fy * size)) }
        ctx.move(to: p(0.27, 0.52)); ctx.addLine(to: p(0.43, 0.67)); ctx.addLine(to: p(0.73, 0.35))
        ctx.strokePath()
    }

    func save(_ name: String) {
        let url = URL(fileURLWithPath: "\(outDir)/\(name).png")
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
        CGImageDestinationFinalize(dest)
        print(url.path)
    }
}

// MARK: - the posters

let lockCrop = CGRect(x: 0, y: 112, width: 1080, height: 2424 - 112)          // drops the status bar
let appCrop = CGRect(x: 0, y: 112, width: 1080, height: 2424 - 112 - 130)     // drops the status bar and the navigation bar

/// The top of every poster: the crescent and the app's name, then the headline and one line under it. Returns where the content starts.
func poster(_ headline: String, _ subline: String) -> (Canvas, CGFloat) {
    let c = Canvas(1080, 1920)
    c.crescent(centerX: 540, top: 70, size: 74)
    c.text("Prayer Live Wallpaper", x: 0, top: 156, width: 1080, size: 30, bold: false, color: grey)
    c.text(headline, x: 70, top: 236, width: 940, size: 96, bold: true, color: white)
    let headlineHeight = c.textHeight(headline, width: 940, size: 96, bold: true)
    let subTop = 236 + headlineHeight + 26
    c.text(subline, x: 110, top: subTop, width: 860, size: 42, bold: false, color: grey, line: 1.25)
    let subHeight = c.textHeight(subline, width: 860, size: 42, bold: false, line: 1.25)
    return (c, subTop + subHeight + 60)
}

let lockStd = load("lock_std"), lockLarge = load("lock_large"), lockXL = load("lock_xl")
let lockHigh = load("lock_high"), lockLow = load("lock_low")
let appLocation = load("app_location"), appReminders = load("app_reminders"), appAccess = load("app_access"), shade = load("shade")

// 1. The wallpaper itself.
do {
    let (c, top) = poster("Prayer times on your lock screen", "A live wallpaper that updates itself.")
    c.phone(lockStd, crop: lockCrop, x: 190, top: top + 20, width: 700)
    c.save("poster-1-lock-screen")
}

// 2. Accurate to MUIS.
do {
    let (c, top) = poster("Accurate to MUIS", "Singapore prayer times and the Hijri calendar follow the official MUIS timetable.")
    c.phone(lockStd, crop: lockCrop, x: 50, top: top + 10, width: 500)
    c.phone(appLocation, crop: appCrop, x: 530, top: top + 110, width: 500)
    c.ctx.setFillColor(black)
    c.ctx.fill(c.rect(0, 1810, 1080, 110))
    c.text("An independent app. Not affiliated with or endorsed by MUIS.", x: 60, top: 1846, width: 960, size: 28, bold: false, color: grey)
    c.save("poster-2-accurate-to-muis")
}

// 3. Reliable notifications.
do {
    let (c, top) = poster("Reminders that arrive on time", "A notification at each prayer time, with a health check that shows what could stop it.")
    // The notification card cut from the shade screenshot (the card only, not the quick settings above it).
    c.card(shade, crop: CGRect(x: 40, y: 640, width: 1000, height: 280), x: 70, top: top, width: 940, radius: 40)
    c.phone(appReminders, crop: appCrop, x: 190, top: top + 330, width: 700)
    c.save("poster-3-reminders")
}

// 4. Accessibility.
do {
    let (c, top) = poster("Easier to read", "Larger text, a high contrast card and bold letters. Your phone's settings are followed.")
    c.phone(lockLarge, crop: lockCrop, x: 50, top: top + 10, width: 500)
    c.phone(lockXL, crop: lockCrop, x: 530, top: top + 110, width: 500)
    c.save("poster-4-accessibility")
}

// 5. Battery.
do {
    let (c, top) = poster("Light on battery", "Built to use almost no battery.")
    let rows = ["No foreground service", "No wake locks", "Redraws only while your screen is on"]
    var y = top
    for row in rows {
        c.tick(x: 90, top: y + 6, size: 64)
        c.text(row, x: 184, top: y + 8, width: 820, size: 46, bold: true, color: white, align: .left)
        y += 108
    }
    c.phone(lockStd, crop: lockCrop, x: 270, top: y + 40, width: 540)
    c.save("poster-5-battery")
}

// 6. The list is movable (the shots differ only in where the list sits: 25% and 62% of the way down).
do {
    let (c, top) = poster("Move the list", "Drag it up or down in the app, so it sits clear of your notifications.")
    c.phone(lockHigh, crop: lockCrop, x: 50, top: top + 10, width: 500)
    c.phone(lockLow, crop: lockCrop, x: 530, top: top + 110, width: 500)
    c.save("poster-6-movable-list")
}

// MARK: - Play graphics

// The feature graphic, 1024x500: the name and the one-line promise, with the lock screen on the right.
do {
    let c = Canvas(1024, 500)
    c.crescent(centerX: 130, top: 60, size: 90)
    c.text("Prayer Live\nWallpaper", x: 60, top: 170, width: 520, size: 68, bold: true, color: white, align: .left, line: 1.05)
    c.text("Prayer times on your lock screen.", x: 60, top: 330, width: 480, size: 30, bold: false, color: grey, align: .left)
    c.phone(lockStd, crop: lockCrop, x: 640, top: 50, width: 330)
    c.save("feature-graphic-1024x500")
}

// The icon, 512x512: the crescent on black, full bleed (Play rounds the corners itself).
do {
    let c = Canvas(512, 512)
    c.crescent(centerX: 256, top: 78, size: 360)
    c.save("icon-512")
}
