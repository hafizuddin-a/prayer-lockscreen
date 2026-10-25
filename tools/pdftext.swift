// Dev tool: prints the text of a PDF page by page, so a MUIS calendar PDF can be read and checked mechanically.
// This Mac has no poppler, but macOS ships PDFKit.
//   swift tools/pdftext.swift "Key Islamic dates 2028.pdf" > /tmp/2028.txt
//   swift tools/pdftext.swift in.pdf outdir     also renders each page to outdir/page-N.png, for looking at
import Foundation
import PDFKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let a = CommandLine.arguments
guard a.count >= 2, let doc = PDFDocument(url: URL(fileURLWithPath: a[1])) else {
    FileHandle.standardError.write(Data("usage: swift pdftext.swift in.pdf [outdir]\n".utf8))
    exit(1)
}
if a.count > 2 { try? FileManager.default.createDirectory(atPath: a[2], withIntermediateDirectories: true) }
for i in 0..<doc.pageCount {
    guard let page = doc.page(at: i) else { continue }
    print("===== PAGE \(i + 1) of \(doc.pageCount) =====")
    print(page.string ?? "(no text layer: read the rendered page instead)")
    if a.count > 2 {
        let box = page.bounds(for: .mediaBox)
        let scale: CGFloat = 2.0
        let w = Int(box.width * scale), h = Int(box.height * scale)
        let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.scaleBy(x: scale, y: scale)
        page.draw(with: .mediaBox, to: ctx)
        let out = URL(fileURLWithPath: "\(a[2])/page-\(i + 1).png")
        let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
        CGImageDestinationFinalize(dest)
    }
}
