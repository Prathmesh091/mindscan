"""
MindScan AI - Model Training Pipeline
Merges Sleep_health_and_lifestyle_dataset.csv + sleep_mobile_stress_dataset_15000.csv
Engineers 7 extra features, applies RobustScaler + SMOTE,
trains ANN + GBM + RandomForest + ExtraTrees, ensemble, saves model files.
Target accuracy: 89%+
"""
import os
# Always save models to the same folder as this script
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(BASE_DIR)
print("Working directory:", os.getcwd())

import warnings
warnings.filterwarnings('ignore')
import numpy as np
import pandas as pd
import joblib
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

from sklearn.ensemble import RandomForestClassifier, ExtraTreesClassifier, GradientBoostingClassifier
from sklearn.preprocessing import RobustScaler, LabelEncoder
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score
from imblearn.over_sampling import SMOTE

import tensorflow as tf
import keras
from keras import layers
from keras.callbacks import EarlyStopping, ReduceLROnPlateau

tf.random.set_seed(42)
np.random.seed(42)

print("Loading datasets...")

# ── Dataset 1: Sleep Health ──────────────────────────────────────────────────
df1 = pd.read_csv(os.path.join(BASE_DIR, "sleep_health_and_lifestyle_dataset.csv"))
df1 = df1.rename(columns={
    'Age': 'age',
    'Sleep Duration': 'sleep_duration',
    'Quality of Sleep': 'quality_of_sleep',
    'Physical Activity Level': 'physical_activity_level',
    'Daily Steps': 'daily_steps',
    'Stress Level': 'stress_raw'
})
df1['stress_normalized'] = ((df1['stress_raw'] - df1['stress_raw'].min()) /
                             (df1['stress_raw'].max() - df1['stress_raw'].min())) * 9 + 1
np.random.seed(42)
df1['daily_screen_time_hours']      = np.random.uniform(2, 10, len(df1))
df1['social_media_usage_hours']     = np.random.uniform(0.5, 4, len(df1))
df1['gaming_app_usage_hours']       = np.random.uniform(0, 3, len(df1))
df1['productivity_app_usage_hours'] = np.random.uniform(0.5, 5, len(df1))
df1['mental_fatigue_score']         = np.random.uniform(2, 9, len(df1))
df1['phone_before_sleep_min']       = np.random.uniform(0, 90, len(df1))

# ── Dataset 2: Mobile Stress ─────────────────────────────────────────────────
df2 = pd.read_csv(os.path.join(BASE_DIR, "sleep_mobile_stress_dataset_15000.csv"))
df2.columns = df2.columns.str.strip()
df2 = df2.rename(columns={
    'age':                             'age',
    'sleep_duration_hours':            'sleep_duration',
    'sleep_quality_score':             'quality_of_sleep',
    'physical_activity_minutes':       'physical_activity_level',
    'daily_screen_time_hours':         'daily_screen_time_hours',
    'phone_usage_before_sleep_minutes':'phone_before_sleep_min',
    'mental_fatigue_score':            'mental_fatigue_score',
    'stress_level':                    'stress_raw'
})
df2['stress_normalized'] = df2['stress_raw']
np.random.seed(42)
df2['daily_steps']                  = np.random.randint(2000, 15000, len(df2))
df2['social_media_usage_hours']     = np.random.uniform(0.5, 4, len(df2))
df2['gaming_app_usage_hours']       = np.random.uniform(0, 3, len(df2))
df2['productivity_app_usage_hours'] = np.random.uniform(0.5, 5, len(df2))
df2['physical_activity_level'] = (df2['physical_activity_level'] /
                                   df2['physical_activity_level'].max() * 100).astype(int)

# ── Merge ─────────────────────────────────────────────────────────────────────
cols = ['age','sleep_duration','quality_of_sleep','physical_activity_level',
        'daily_steps','daily_screen_time_hours','social_media_usage_hours',
        'gaming_app_usage_hours','productivity_app_usage_hours',
        'mental_fatigue_score','phone_before_sleep_min','stress_normalized']

df = pd.concat([df1[cols], df2[cols]], ignore_index=True)
df = df.dropna()
print(f"Total samples: {len(df)}")

# ── Labels ────────────────────────────────────────────────────────────────────
def label_stress(v):
    if v <= 5.55:   return 'Low'
    elif v <= 9.03: return 'Medium'
    else:           return 'High'

df['stress_label'] = df['stress_normalized'].apply(label_stress)
print("Label distribution:\n", df['stress_label'].value_counts())

# ── Feature Engineering ───────────────────────────────────────────────────────
df['sleep_deficit']             = np.maximum(0, 8 - df['sleep_duration'])
df['sleep_score']               = df['sleep_duration'] * df['quality_of_sleep']
df['digital_stress_ratio']      = df['daily_screen_time_hours'] / (df['sleep_duration'] + 1)
df['activity_score']            = df['physical_activity_level'] * df['daily_steps'] / 1000
df['stress_risk_index']         = df['sleep_deficit'] + df['digital_stress_ratio'] + df['mental_fatigue_score']
df['fatigue_sleep_interaction'] = df['mental_fatigue_score'] * df['sleep_deficit']
df['nightscreen_risk']          = df['phone_before_sleep_min'] * df['daily_screen_time_hours'] / 10

features = ['age','sleep_duration','quality_of_sleep','physical_activity_level',
            'daily_steps','daily_screen_time_hours','social_media_usage_hours',
            'gaming_app_usage_hours','productivity_app_usage_hours',
            'mental_fatigue_score','phone_before_sleep_min',
            'sleep_deficit','sleep_score','digital_stress_ratio',
            'activity_score','stress_risk_index','fatigue_sleep_interaction','nightscreen_risk']

X = df[features].values
le = LabelEncoder()
y = le.fit_transform(df['stress_label'])

# ── Scale + Balance ───────────────────────────────────────────────────────────
scaler = RobustScaler()
X_scaled = scaler.fit_transform(X)

smote = SMOTE(random_state=42)
X_bal, y_bal = smote.fit_resample(X_scaled, y)
print(f"After SMOTE: {X_bal.shape[0]} samples")

X_train, X_test, y_train, y_test = train_test_split(
    X_bal, y_bal, test_size=0.2, random_state=42, stratify=y_bal)

# ── ANN ───────────────────────────────────────────────────────────────────────
print("\nTraining ANN...")
model_ann = keras.Sequential([
    layers.Input(shape=(18,)),
    layers.Dense(512, activation='relu'),
    layers.BatchNormalization(),
    layers.Dropout(0.3),
    layers.Dense(256, activation='relu'),
    layers.BatchNormalization(),
    layers.Dropout(0.2),
    layers.Dense(128, activation='relu'),
    layers.Dropout(0.2),
    layers.Dense(64, activation='relu'),
    layers.Dense(3, activation='softmax')
])
model_ann.compile(optimizer='adam',
                  loss='sparse_categorical_crossentropy',
                  metrics=['accuracy'])

callbacks = [
    EarlyStopping(patience=5, restore_best_weights=True),
    ReduceLROnPlateau(patience=3, factor=0.5)
]
model_ann.fit(X_train, y_train, epochs=50, batch_size=32,
              validation_split=0.1, callbacks=callbacks, verbose=1)

# ── GBM ───────────────────────────────────────────────────────────────────────
print("\nTraining GBM...")
model_gbm = GradientBoostingClassifier(n_estimators=200, max_depth=5,
                                        learning_rate=0.1, random_state=42)
model_gbm.fit(X_train, y_train)

# ── Random Forest ─────────────────────────────────────────────────────────────
print("\nTraining Random Forest...")
model_rf = RandomForestClassifier(n_estimators=200, max_depth=10,
                                   random_state=42, n_jobs=-1)
model_rf.fit(X_train, y_train)

# ── Extra Trees ───────────────────────────────────────────────────────────────
print("\nTraining Extra Trees...")
model_et = ExtraTreesClassifier(n_estimators=200, random_state=42, n_jobs=-1)
model_et.fit(X_train, y_train)

# ── Ensemble ──────────────────────────────────────────────────────────────────
print("\nEvaluating ensemble...")
weights = [0.35, 0.30, 0.20, 0.15]
ann_proba = model_ann.predict(X_test, verbose=0)
gbm_proba = model_gbm.predict_proba(X_test)
rf_proba  = model_rf.predict_proba(X_test)
et_proba  = model_et.predict_proba(X_test)

ensemble = (weights[0]*ann_proba + weights[1]*gbm_proba +
            weights[2]*rf_proba  + weights[3]*et_proba)
y_pred = np.argmax(ensemble, axis=1)

acc = accuracy_score(y_test, y_pred)
print(f"\n✅ Ensemble Accuracy: {acc*100:.2f}%")
print(classification_report(y_test, y_pred, target_names=le.classes_))

# ── Save ──────────────────────────────────────────────────────────────────────
print("\nSaving models...")
model_ann.save(os.path.join(BASE_DIR, 'ann_model.keras'))
joblib.dump(model_gbm, os.path.join(BASE_DIR, 'gbm_model.pkl'))
joblib.dump(model_rf,  os.path.join(BASE_DIR, 'rf_model.pkl'))
joblib.dump(model_et,  os.path.join(BASE_DIR, 'et_model.pkl'))
joblib.dump(scaler,    os.path.join(BASE_DIR, 'scaler.pkl'))
joblib.dump(le,        os.path.join(BASE_DIR, 'label_encoder.pkl'))
joblib.dump(features,  os.path.join(BASE_DIR, 'feature_cols.pkl'))
joblib.dump({
    'ensemble_accuracy': acc,
    'weights': weights,
    'class_names': list(le.classes_)
}, os.path.join(BASE_DIR, 'model_meta.pkl'))

print(f"\n✅ All models saved to: {BASE_DIR}")
print("   ann_model.keras | gbm_model.pkl | rf_model.pkl | et_model.pkl")
print("   scaler.pkl | label_encoder.pkl | feature_cols.pkl | model_meta.pkl")
