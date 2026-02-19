from fastapi import FastAPI

app = FastAPI(title="ZoneTrack Asset API")

@app.get("/")
async def root():
    return {"message": "Asset API is running"}
