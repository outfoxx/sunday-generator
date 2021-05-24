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
        .package(url: "https://github.com/outfoxx/sunday-swift.git", .revision("2b28d4699782e84f9020612860a50a19c464ba6d"))
    ],
    targets: [
        .target(
          name: "SundayGenTest",
          dependencies: ["Sunday"],
          path: "src"
        )
    ]
)
