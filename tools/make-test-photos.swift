// Dev tool: writes two synthetic test photos (dark night scene, bright day scene) for photo-mode tests.
// usage: swift make-test-photos.swift outDir
import Foundation
import CoreGraphics
import ImageIO

let out = CommandLine.arguments[1]
let w = 1200, h = 2400

func render(_ name: String, top: [CGFloat], bottom: [CGFloat], sun: [CGFloat], ground: [CGFloat]) {
    let space = CGColorSpaceCreateDeviceRGB()
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let gradient = CGGradient(colorsSpace: space, colors: [
        CGColor(red: top[0], green: top[1], blue: top[2], alpha: 1),
        CGColor(red: bottom[0], green: bottom[1], blue: bottom[2], alpha: 1),
    ] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: CGFloat(h)), end: CGPoint(x: 0, y: 0), options: [])
    // a sun or moon
    ctx.setFillColor(CGColor(red: sun[0], green: sun[1], blue: sun[2], alpha: 1))
    ctx.fillEllipse(in: CGRect(x: 760, y: 1500, width: 260, height: 260))
    // hills
    ctx.setFillColor(CGColor(red: ground[0], green: ground[1], blue: ground[2], alpha: 1))
    let hills = CGMutablePath()
    hills.move(to: CGPoint(x: 0, y: 0))
    hills.addLine(to: CGPoint(x: 0, y: 700))
    hills.addCurve(to: CGPoint(x: 600, y: 760), control1: CGPoint(x: 200, y: 950), control2: CGPoint(x: 400, y: 500))
    hills.addCurve(to: CGPoint(x: 1200, y: 640), control1: CGPoint(x: 800, y: 1000), control2: CGPoint(x: 1000, y: 560))
    hills.addLine(to: CGPoint(x: 1200, y: 0))
    ctx.addPath(hills); ctx.fillPath()
    let image = ctx.makeImage()!
    let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: "\(out)/\(name).jpg") as CFURL, "public.jpeg" as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
    CGImageDestinationFinalize(dest)
}
render("test-dark",  top: [0.02, 0.03, 0.10], bottom: [0.08, 0.10, 0.24], sun: [0.9, 0.9, 0.8], ground: [0.01, 0.02, 0.05])
render("test-light", top: [0.62, 0.80, 0.97], bottom: [0.97, 0.97, 0.92], sun: [1.0, 0.95, 0.6], ground: [0.55, 0.78, 0.45])
