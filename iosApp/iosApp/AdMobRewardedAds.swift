import GoogleMobileAds
import Shared
import UIKit

/// Rewarded ads through Google AdMob, for the game's `RewardedAds` interface. One ad is loaded
/// ahead so it shows at once; asking while none is ready loads one and shows it when it arrives.
/// Nothing loads until `start()`, which the app calls once the tracking prompt is answered.
final class AdMobRewardedAds: NSObject, RewardedAds, FullScreenContentDelegate {
    /// Google's sample rewarded ad unit, which always fills with a test ad.
    static let testAdUnitID = "ca-app-pub-3940256099942544/1712485313"

    private let adUnitID: String
    private var started = false
    private var ready: RewardedAd?
    private var loading = false
    private var earned = false
    /// The answer for the ad on screen.
    private var showing: ((KotlinBoolean) -> Void)?
    /// The answer waiting on an ad that is still loading.
    private var waiting: ((KotlinBoolean) -> Void)?

    /// Reads the ad unit from Info.plist (`AdMobRewardedAdUnitID`), falling back to the test unit.
    override init() {
        let configured = Bundle.main.object(forInfoDictionaryKey: "AdMobRewardedAdUnitID") as? String ?? ""
        adUnitID = configured.isEmpty ? Self.testAdUnitID : configured
        super.init()
    }

    /// Starts the ad SDK and loads the first ad.
    func start() {
        guard !started else { return }
        started = true
        MobileAds.shared.start { [weak self] _ in self?.load() }
    }

    func show(onFinished: @escaping (KotlinBoolean) -> Void) {
        guard let ad = ready else {
            waiting?(KotlinBoolean(bool: false)) // Only the latest ask waits.
            waiting = onFinished
            start()
            load()
            return
        }
        guard let host = Self.topViewController() else {
            onFinished(KotlinBoolean(bool: false))
            return
        }
        ready = nil
        earned = false
        showing = onFinished
        ad.fullScreenContentDelegate = self
        ad.present(from: host) { [weak self] in self?.earned = true }
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        finish(rewarded: earned)
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        finish(rewarded: false)
    }

    private func finish(rewarded: Bool) {
        let onFinished = showing
        showing = nil
        onFinished?(KotlinBoolean(bool: rewarded))
        load()
    }

    private func load() {
        guard started, ready == nil, !loading else { return }
        loading = true
        RewardedAd.load(with: adUnitID, request: Request()) { [weak self] ad, _ in
            guard let self else { return }
            self.loading = false
            self.ready = ad
            guard let onFinished = self.waiting else { return }
            self.waiting = nil
            if ad != nil {
                self.show(onFinished: onFinished)
            } else {
                onFinished(KotlinBoolean(bool: false))
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
