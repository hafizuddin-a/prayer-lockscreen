// Makes a QR code as a plain SVG (black squares on white, with the 4-module quiet zone) using macOS's own QR generator (no packages needed).
//   swift tools/make-qr.swift https://hafizuddin-a.github.io/prayer-lockscreen/download/ site/assets/download-qr.svg
// Scan the result with a phone before publishing it.
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

let args = CommandLine.arguments
guard args.count == 3 else { print("usage: swift tools/make-qr.swift <text> <out.svg>"); exit(1) }
let text = args[1], out = args[2]

let filter = CIFilter.qrCodeGenerator()
filter.message = Data(text.utf8)
filter.correctionLevel = "M"
guard let image = filter.outputImage else { print("no output"); exit(1) }

// The generator gives one pixel per module. Read them back as a grid.
let context = CIContext(options: [.useSoftwareRenderer: true])
let extent = image.extent.integral
let width = Int(extent.width), height = Int(extent.height)
guard let cg = context.createCGImage(image, from: extent) else { print("no bitmap"); exit(1) }
var pixels = [UInt8](repeating: 0, count: width * height * 4)
let space = CGColorSpaceCreateDeviceRGB()
let drawn = CGContext(data: &pixels, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: space,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
drawn.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
func dark(_ x: Int, _ y: Int) -> Bool { pixels[(y * width + x) * 4] < 128 }

// Trim to the symbol itself (the finder squares touch its corners), then add our own quiet zone.
var minX = width, minY = height, maxX = -1, maxY = -1
for y in 0..<height { for x in 0..<width where dark(x, y) { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) } }
let n = maxX - minX + 1
guard n == maxY - minY + 1 else { print("not square"); exit(1) }
let quiet = 4
let total = n + 2 * quiet

// One path of unit squares: small, crisp at any size.
var path = ""
for y in 0..<n {
    var x = 0
    while x < n {
        if dark(minX + x, minY + y) {
            var run = 1
            while x + run < n && dark(minX + x + run, minY + y) { run += 1 }
            path += "M\(x + quiet) \(y + quiet)h\(run)v1h-\(run)z"
            x += run
        } else { x += 1 }
    }
}
let svg = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 \(total) \(total)" width="\(total * 8)" height="\(total * 8)" shape-rendering="crispEdges" role="img" aria-label="QR code for \(text)">
<rect width="\(total)" height="\(total)" fill="#fff"/>
<path fill="#000" d="\(path)"/>
</svg>

"""
try svg.write(toFile: out, atomically: true, encoding: .utf8)
print("modules: \(n)x\(n), written \(out)")
