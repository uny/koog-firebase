// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "dev_ynagai_firebase_firebase_ai_0_10_0",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "dev_ynagai_firebase_firebase_ai_0_10_0",
      type: .none,
      targets: ["dev_ynagai_firebase_firebase_ai_0_10_0"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/uny/firebase-objc-sdk.git",
      from: "0.5.0"
    )
  ],
  targets: [
    .target(
      name: "dev_ynagai_firebase_firebase_ai_0_10_0",
      dependencies: [
        .product(
          name: "FirebaseAILogicObjC",
          package: "firebase-objc-sdk"
        )
      ]
    )
  ]
)
