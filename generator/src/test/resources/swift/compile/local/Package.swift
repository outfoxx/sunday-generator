// swift-tools-version:6.2
import PackageDescription

let package = Package(
    name: "SundayGenTest",
    platforms: [
      .macOS(.v15)
    ],
    products: [
        .library(name: "SundayGenTest", targets: ["SundayGenTest"]),
    ],
    dependencies: [
        .package(url: "https://github.com/outfoxx/sunday-swift.git", exact: "2.0.0-beta.2")
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
