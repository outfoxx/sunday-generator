// swift-tools-version:5.0
import PackageDescription

let package = Package(
    name: "SundayGenTest",
    products: [
        .library(name: "SundayGenTest", targets: ["SundayGenTest"]),
    ],
    targets: [
        .target(
          name: "SundayGenTest",
          dependencies: ["Sunday", "PotentCodables"],
          path: "src"
        ),
        .target(
          name: "Sunday",
          dependencies: ["PotentCodables"],
          path: "sunday"
        ),
        .target(
          name: "PotentCodables",
          path: "pc"
        )
    ]
)
