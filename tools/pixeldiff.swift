// Counts the pixels that differ between two screenshots inside a region. Dev tool, for "did this
// refactor change what is drawn?" checks. Exits 0 when the regions are identical, 1 otherwise.
// usage: swift pixeldiff.swift a.png b.png x y w h [tolerance]
// tolerance (default 0) is the largest per-channel difference still counted as equal.
import Foundation
import CoreGraphics
import ImageIO

func load(_ path: String) -> CGImage {
    guard let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
        FileHandle.standardError.write(Data("cannot read \(path)\n".utf8))
        exit(2)
    }
    return image
}

func pixels(_ image: CGImage, _ r: CGRect) -> [UInt8] {
    let w = Int(r.width), h = Int(r.height)
    var data = [UInt8](repeating: 0, count: w * h * 4)
    let ctx = CGContext(data: &data, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.draw(image.cropping(to: r)!, in: CGRect(x: 0, y: 0, width: w, height: h))
    return data
}

let a = CommandLine.arguments
guard a.count >= 7 else {
    print("usage: pixeldiff a.png b.png x y w h [tolerance]")
    exit(2)
}
let region = CGRect(x: Double(a[3])!, y: Double(a[4])!, width: Double(a[5])!, height: Double(a[6])!)
let tolerance = a.count > 7 ? Int(a[7])! : 0
let first = pixels(load(a[1]), region), second = pixels(load(a[2]), region)
var differing = 0, worst = 0
for p in stride(from: 0, to: first.count, by: 4) {
    let d = (0..<3).map { abs(Int(first[p + $0]) - Int(second[p + $0])) }.max()!
    worst = max(worst, d)
    if d > tolerance { differing += 1 }
}
print("\(differing) of \(first.count / 4) pixels differ (largest channel difference \(worst))")
exit(differing == 0 ? 0 : 1)
