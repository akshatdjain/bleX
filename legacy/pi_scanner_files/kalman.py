# kalman.py
# -------------------------------------------------
# RSSI-specific Kalman Filter
# -------------------------------------------------

from config import KALMAN_Q, KALMAN_R


class KalmanRSSI:
    def __init__(self, process_noise=KALMAN_Q, measurement_noise=KALMAN_R):
        # Initial RSSI estimate
        self.x = -70.0
        self.p = 10.0

        # Noise parameters
        self.q = process_noise
        self.r = measurement_noise

    def update(self, measurement):
        # Prediction
        self.p = self.p + self.q

        # Kalman gain
        k = self.p / (self.p + self.r)

        # Update estimate
        self.x = self.x + k * (measurement - self.x)

        # Update covariance
        self.p = (1 - k) * self.p

        return self.x
