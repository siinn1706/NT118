# Camera PPG Heart Rate Validation Protocol

## Objective

Validate the accuracy of the HealthOS Android rear-camera photoplethysmography (PPG) heart-rate estimator against a reference fingertip pulse oximeter at rest.

- **Target accuracy:** MAE ≤ 5 BPM at rest.
- **Disclaimer:** This feature is for personal wellness tracking only and does not replace a certified medical device. No SpO2 or blood-pressure claims are made.

## Collection Procedure

1. Two subjects, 10 paired readings each (20 total paired readings).
2. Each subject sits quietly at rest for at least 1 minute before starting.
3. Place the fingertip Pulse Oximeter on the left index finger and cover the Pixel 8 Pro rear main lens + LED flash gently with the right index finger.
4. Run a 20-second measurement in HealthOS (`Tôi → Đo nhịp tim`). Record the final `ppg_bpm` and the concurrent stable `oximeter_bpm` in [ppg-validation.csv](./ppg-validation.csv).
5. Wait 30 seconds between trials so the LED stays cool.

## Analysis

Run the companion script after populating `docs/ppg-validation.csv`:

```bash
python tools/ppg_validation.py
```

The script computes MAE, RMSE, MAPE, mean bias, and 95% Bland–Altman limits of agreement, and writes `docs/ppg-bland-altman.png` when `matplotlib` is installed.
