FROM python:3.11-slim

# Install Bluetooth dependencies for Linux/Raspberry Pi
RUN apt-get update && apt-get install -y --no-install-recommends \
    bluez \
    dbus \
    libglib2.0-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Run Bleak in real mode on Raspberry Pi with host networking / DBus access
CMD ["python3", "-m", "src.main", "--mode", "real"]
