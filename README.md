# AIEye_Camera

## Yolov8를 이용한 화재 감지 모델을 사용합니다. onnx로 모델 변환 후, OnnxRuntime 라이브러리를 이용합니다.
------------

#### onnx 변환은 yolov8 공식사이트(https://github.com/ultralytics/ultralytics) 를 사용했습니다. 
  * 변환 방식입니다. (공식사이트에도 있습니다.)
   '''
  from ultralytics import YOLO

  # Load a model
  model = YOLO("학습한 모델 경로")

  # Use the model
  success = model.export(format="onnx")  # export the model to ONNX format
   '''
    
------------

#### 사용법은 아래와 같습니다.
  + 초기 사용자의 경우 
    + 1. 블루투스 켜기 버튼을 누르고 블루투스 연결하기 버튼을 통해 모터 제어를 할 기기와 블루투스 연결을 합니다. (제어할 모터가 없는 경우 생략합니다.)
    + 2. QR코드 스캔하기를 눌러 사진 전송하거나 객체 검출시 나온 내용을 보낼 서버와 연동합니다. 서버에서 제공하는 QR코드를 찍으시면 됩니다. (해당 연구실만 사용가능 합니다.)
    + 3. motion 감지 or Object 검출 중 체크 박스를 통해 하나만 선택합니다.
    + 4. 저장 및 실행 버튼을 눌러 앱을 실행합니다.
  + 기존 사용자의 경우
    + 1. motion 감지 or Object 검출 중 체크 박스를 통해 하나만 선택합니다.
    + 2. 초기 설정에 대한 내용이 RoomDB에 저장되있으므로, 저장 및 실행을 눌러 앱을 실행합니다.
        
------------

#### 어플의 주요 내용은 아래와 같습니다.
  1. 실시간 사진 전송
  2. 실시간 객체 검출 or 실시간 움직임 감지
  3. 실시간 영상 전송
  4. 실시간 모터 제어 
