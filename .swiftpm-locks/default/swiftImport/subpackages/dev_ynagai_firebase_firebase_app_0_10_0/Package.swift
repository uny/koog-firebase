// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "dev_ynagai_firebase_firebase_app_0_10_0",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "dev_ynagai_firebase_firebase_app_0_10_0",
      type: .none,
      targets: ["dev_ynagai_firebase_firebase_app_0_10_0"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      from: "12.14.0"
    )
  ],
  targets: [
    .target(
      name: "dev_ynagai_firebase_firebase_app_0_10_0",
      dependencies: [
        .product(
          name: "FirebaseCore",
          package: "firebase-ios-sdk"
        )
      ]
    )
  ]
)
