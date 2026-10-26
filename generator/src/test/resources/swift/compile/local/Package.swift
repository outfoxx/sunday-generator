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
        .package(url: "https://github.com/outfoxx/sunday-swift.git", revision: "72861ae5731e9fa7d63677f07a9a158f9291f6c2")
    ],
    targets: [
        .target(
          name: "SundayGenTest",
          dependencies: [
            .product(name: "Sunday", package: "sunday-swift")
          ],
          path: "src"
        ),
        .testTarget(
          name: "SundayGenTestTests",
          dependencies: ["SundayGenTest"],
          path: "tests"
        )
    ]
)
