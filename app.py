from flask import Flask, send_from_directory, jsonify, send_file
import os, io, zipfile

app = Flask(__name__)
UPLOAD_FOLDER = "NeoPass"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.route("/download/<filename>")
def download_file(filename):
    return send_from_directory(UPLOAD_FOLDER, filename, as_attachment=True)

@app.route("/list")
def list_files():
    files = os.listdir(UPLOAD_FOLDER)
    return jsonify(files)

@app.route("/download_all")
def download_all():
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(UPLOAD_FOLDER):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, UPLOAD_FOLDER)
                zipf.write(file_path, arcname)
    zip_buffer.seek(0)
    return send_file(
        zip_buffer,
        as_attachment=True,
        download_name="ftp_files.zip",
        mimetype="application/zip"
    )


@app.route("/")
def index():
    files = os.listdir(UPLOAD_FOLDER)
    return jsonify({
        "message": "Flask pseudo-FTP server running",
        "available_files": files,
        "download_example": "curl -O https://<your-render-url>/download/<filename>"
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=10000)
