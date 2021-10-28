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
        .package(url: "https://github.com/outfoxx/sunday-swift.git", .exact("1.0.0-beta.6"))
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
