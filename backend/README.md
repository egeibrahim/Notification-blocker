# Notifilter Backend

## Setup

1. Create a virtualenv and install dependencies:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

2. Create `.env` based on `.env.example`.

3. Run the API:

```bash
uvicorn app.main:app --reload --port 8000
```

Health check:

- `GET http://localhost:8000/health`
