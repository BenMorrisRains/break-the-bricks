import AppTrackingTransparency
import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let ads: AdMobRewardedAds

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(ads: ads)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var ads = AdMobRewardedAds()

    var body: some View {
        ComposeView(ads: ads)
            .ignoresSafeArea()
            .background(Color.black)
            .onAppear {
                if UIApplication.shared.applicationState == .active { askToTrackThenStartAds() }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                askToTrackThenStartAds()
            }
    }

    /// Apple asks before personalized ads (App Tracking Transparency). The prompt only shows
    /// while the app is active, and only the first time; ads start whatever the answer.
    private func askToTrackThenStartAds() {
        ATTrackingManager.requestTrackingAuthorization { _ in
            DispatchQueue.main.async { ads.start() }
        }
    }
}
