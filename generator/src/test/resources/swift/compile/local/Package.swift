// swift-tools-version:5.4
import PackageDescription

let package = Package(
    name: "SundayGenTest",
    platforms: [
      .macOS(.v10_15)
    ],
    products: [
        .library(name: "SundayGenTest", targets: ["SundayGenTest"]),
    ],
    dependencies: [
        .package(url: "https://github.com/outfoxx/sunday-swift.git", from: "1.0.0-beta.11")
    ],
    targets: [
        .target(
          name: "SundayGenTest",
          dependencies: [
            .product(name: "Sunday", package: "sunday-swift")
          ],
          path: "src"
        )
    ]
)
