FROM python:3.12-slim
LABEL author="Hongs"
LABEL description="Document parser server, use markitdown"
WORKDIR /app
COPY .env .
COPY server.py .
COPY parser.py .
COPY requirements.txt .
RUN pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --no-cache-dir
EXPOSE 8090
ENTRYPOINT ["python", "server.py"]