from config import KALMAN_Q, KALMAN_R

class KalmanRSSI:
    def __init__(self, process_noise=KALMAN_Q, measurement_noise=KALMAN_R):
        self.x = -70.0
        self.p = 10.0
        self.q = process_noise
        self.r = measurement_noise

    def update(self, measurement):
        self.p = self.p + self.q
        k = self.p / (self.p + self.r)
        self.x = self.x + k * (measurement - self.x)
        self.p = (1 - k) * self.p
        return self.x
