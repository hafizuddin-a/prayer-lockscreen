// Side-by-side / grid image composer for visual comparison, with optional labels.
// usage: [COLS=n] swift compose.swift out.png height  path x y w h label  [path x y w h label ...]
// Each cropped region is scaled to `height` pixels tall; panels wrap into rows of COLS (default: one row).
import Foundation
import CoreGraphics
import CoreText
import ImageIO

func load(_ path: String) -> CGImage {
    let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)!
    return CGImageSourceCreateImageAtIndex(src, 0, nil)!
}
let a = CommandLine.arguments
let out = a[1], height = Int(a[2])!
var panels: [(CGImage, Int, String)] = []
var i = 3
while i + 5 < a.count {
    let r = CGRect(x: Double(a[i+1])!, y: Double(a[i+2])!, width: Double(a[i+3])!, height: Double(a[i+4])!)
    panels.append((load(a[i]).cropping(to: r)!, Int((r.width * Double(height) / r.height).rounded()), a[i+5]))
    i += 6
}
let cols = Int(ProcessInfo.processInfo.environment["COLS"] ?? "") ?? panels.count
let gap = 24
let rows = stride(from: 0, to: panels.count, by: cols).map { Array(panels[$0..<min($0 + cols, panels.count)]) }
let width = rows.map { $0.reduce(0) { $0 + $1.1 } + gap * ($0.count + 1) }.max()!
let totalHeight = rows.count * (height + gap) + gap
let ctx = CGContext(data: nil, width: width, height: totalHeight, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.setFillColor(CGColor(red: 0.11, green: 0.11, blue: 0.11, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: width, height: totalHeight))
ctx.interpolationQuality = .high
let font = CTFontCreateWithName("Helvetica-Bold" as CFString, 22, nil)
for (r, row) in rows.enumerated() {
    var x = gap
    let y = totalHeight - (r + 1) * (height + gap)
    for (img, w, label) in row {
        ctx.draw(img, in: CGRect(x: x, y: y, width: w, height: height))
        if !label.isEmpty {
            let attrs = [kCTFontAttributeName: font, kCTForegroundColorAttributeName: CGColor(red: 1, green: 0.85, blue: 0.3, alpha: 1)] as CFDictionary
            let line = CTLineCreateWithAttributedString(CFAttributedStringCreate(nil, label as CFString, attrs))
            ctx.textPosition = CGPoint(x: x + 10, y: y + height - 28)
            CTLineDraw(line, ctx)
        }
        x += w + gap
    }
}
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: out) as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
CGImageDestinationFinalize(dest)
