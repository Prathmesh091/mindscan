"""
MindScan AI - FastAPI Prediction Service
Port: 8000
Loads ANN + GBM + RF + ET models, runs ensemble, returns stress prediction.
"""
import os
import logging
import numpy as np
import joblib
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional
from tensorflow import keras

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("mindscan")

# Dynamic path - works anywhere
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

app = FastAPI(
    title="MindScan ML Service",
    description="AI Behavioral Stress Monitoring - ML Prediction API",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load models
logger.info("Loading models...")
try:
    ann_model = keras.models.load_model(os.path.join(BASE_DIR, "ann_model.keras"))
    gbm_model = joblib.load(os.path.join(BASE_DIR, "gbm_model.pkl"))
    rf_model  = joblib.load(os.path.join(BASE_DIR, "rf_model.pkl"))
    et_model  = joblib.load(os.path.join(BASE_DIR, "et_model.pkl"))
    scaler    = joblib.load(os.path.join(BASE_DIR, "scaler.pkl"))
    meta      = joblib.load(os.path.join(BASE_DIR, "model_meta.pkl"))
    logger.info(f"All models loaded! Accuracy: {meta.get('ensemble_accuracy', 0):.4f}")
except FileNotFoundError as e:
    logger.error(f"Model file missing: {e}. Run train_model.py first.")
    ann_model = gbm_model = rf_model = et_model = scaler = meta = None

WEIGHTS     = [0.35, 0.30, 0.20, 0.15]
CLASS_NAMES = ["Low", "Medium", "High"]
SCORE_BASES = {"Low": 35, "Medium": 60, "High": 85}


class PredictRequest(BaseModel):
    age: float
    sleep_duration: float
    quality_of_sleep: Optional[float] = None
    sleep_quality: Optional[float] = None
    physical_activity_level: Optional[float] = None
    physical_activity: Optional[float] = None
    daily_steps: float
    daily_screen_time_hours: Optional[float] = None
    screen_time: Optional[float] = None
    social_media_usage_hours: Optional[float] = 1.0
    gaming_app_usage_hours: Optional[float] = 0.5
    productivity_app_usage_hours: Optional[float] = 1.0
    mental_fatigue_score: Optional[float] = None
    mental_fatigue: Optional[float] = None
    phone_before_sleep_min: Optional[float] = None
    phone_before_sleep: Optional[float] = None


def get_val(a, b, default=5.0):
    return a if a is not None else (b if b is not None else default)


def engineer_features(r: PredictRequest):
    sleep_quality      = get_val(r.quality_of_sleep, r.sleep_quality, 5.0)
    physical_activity  = get_val(r.physical_activity_level, r.physical_activity, 30.0)
    screen_time        = get_val(r.daily_screen_time_hours, r.screen_time, 5.0)
    mental_fatigue     = get_val(r.mental_fatigue_score, r.mental_fatigue, 5.0)
    phone_before_sleep = get_val(r.phone_before_sleep_min, r.phone_before_sleep, 30.0)
    social_media       = r.social_media_usage_hours or 1.0
    gaming             = r.gaming_app_usage_hours or 0.5
    productivity       = r.productivity_app_usage_hours or 1.0

    sleep_deficit             = max(0, 8 - r.sleep_duration)
    sleep_score               = r.sleep_duration * sleep_quality
    digital_stress_ratio      = screen_time / (r.sleep_duration + 1)
    activity_score            = physical_activity * r.daily_steps / 1000
    stress_risk_index         = sleep_deficit + digital_stress_ratio + mental_fatigue
    fatigue_sleep_interaction = mental_fatigue * sleep_deficit
    nightscreen_risk          = phone_before_sleep * screen_time / 10

    return np.array([[
        r.age, r.sleep_duration, sleep_quality,
        physical_activity, r.daily_steps,
        screen_time, social_media,
        gaming, productivity,
        mental_fatigue, phone_before_sleep,
        sleep_deficit, sleep_score, digital_stress_ratio,
        activity_score, stress_risk_index,
        fatigue_sleep_interaction, nightscreen_risk
    ]], dtype=np.float32)


def get_recommendations(r: PredictRequest, stress_level: str):
    screen_time        = get_val(r.daily_screen_time_hours, r.screen_time, 5.0)
    mental_fatigue     = get_val(r.mental_fatigue_score, r.mental_fatigue, 5.0)
    phone_before_sleep = get_val(r.phone_before_sleep_min, r.phone_before_sleep, 30.0)
    recs = []
    if r.sleep_duration < 6:
        recs.append("🌙 Aim for 7-9 hours of sleep. Poor sleep amplifies stress.")
    if screen_time > 7:
        recs.append("📵 Reduce daily screen time to under 6 hours.")
    if phone_before_sleep > 60:
        recs.append("📴 Avoid phone use 1 hour before bed.")
    if r.daily_steps < 5000:
        recs.append("🚶 Try to reach 8,000+ steps daily.")
    if mental_fatigue >= 7:
        recs.append("🧘 Practice mindfulness or breathing exercises.")
    if stress_level == "High":
        recs.append("⚠️ High stress detected. Consider speaking with a professional.")
    if not recs:
        recs.append("✅ Your habits look healthy. Keep it up!")
    return recs


@app.get("/health")
def health():
    loaded = all(m is not None for m in [ann_model, gbm_model, rf_model, et_model, scaler])
    return {
        "status": "ok" if loaded else "models_not_loaded",
        "models_loaded": loaded,
        "ensemble_accuracy": meta.get("ensemble_accuracy", 0) if meta else 0
    }


@app.post("/predict")
def predict(req: PredictRequest):
    if any(m is None for m in [ann_model, gbm_model, rf_model, et_model, scaler]):
        raise HTTPException(status_code=503, detail="Models not loaded. Run train_model.py first.")
    try:
        X        = engineer_features(req)
        X_scaled = scaler.transform(X)

        ann_proba = ann_model.predict(X_scaled, verbose=0)
        gbm_proba = gbm_model.predict_proba(X_scaled)
        rf_proba  = rf_model.predict_proba(X_scaled)
        et_proba  = et_model.predict_proba(X_scaled)

        ensemble = (WEIGHTS[0]*ann_proba + WEIGHTS[1]*gbm_proba +
                    WEIGHTS[2]*rf_proba  + WEIGHTS[3]*et_proba)[0]

        idx          = int(np.argmax(ensemble))
        stress_level = CLASS_NAMES[idx]
        confidence   = float(ensemble[idx])
        stress_score = min(100, SCORE_BASES[stress_level] + confidence * 10)

        return {
            "stress_level": stress_level,
            "stress_score": round(stress_score, 1),
            "confidence":   round(confidence, 4),
            "probabilities": {
                CLASS_NAMES[i]: round(float(ensemble[i]), 4)
                for i in range(3)
            },
            "recommendations": get_recommendations(req, stress_level)
        }
    except Exception as e:
        logger.error(f"Prediction error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=False)
