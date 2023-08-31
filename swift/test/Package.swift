// swift-tools-version: 5.8

import PackageDescription

let package = Package(
    name: "test",
    dependencies: [
        .package(path: "../stduritemplate"),
    ],
    targets: [
        .executableTarget(
            name: "test",
            dependencies: ["stduritemplate"],
            path: "Sources"),
    ]
)
