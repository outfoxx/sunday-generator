// swift-tools-version:5.1
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
        .package(url: "https://github.com/outfoxx/sunday-swift.git", from: "1.0.0-beta.1")
    ],
    targets: [
        .target(
          name: "SundayGenTest",
          dependencies: ["Sunday"],
          path: "src"
        )
    ]
)
