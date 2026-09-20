// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(path: "subpackages/_"),
    .package(path: "subpackages/dev_ynagai_firebase_firebase_ai_0_10_0"),
    .package(path: "subpackages/dev_ynagai_firebase_firebase_app_0_10_0")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "_", package: "_"),
        .product(name: "dev_ynagai_firebase_firebase_ai_0_10_0", package: "dev_ynagai_firebase_firebase_ai_0_10_0"),
        .product(name: "dev_ynagai_firebase_firebase_app_0_10_0", package: "dev_ynagai_firebase_firebase_app_0_10_0")
      ]
    )
  ]
)
