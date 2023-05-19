package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.example.camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.pytorch.Module
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    val REQUEST_IMAGE_CAPTURE = 1 // 카메라 사진 촬영 요청 코드
    val REQUEST_GALLERY_IMAGE = 2 // 갤러리 이미지 불러오기
    lateinit var curPhotoPath : String //문자열 형태의 사진 경로 값
    private var mBinding : ActivityMainBinding? = null
    private val binding get () = mBinding!!
    private var mModule: Module? = null
    var checkpreview : Boolean = false
    var checkphoto : Boolean = false

    lateinit var filepath : String
    lateinit var convertfilepath : String
    lateinit var _bm : Bitmap
    lateinit var prebm : Bitmap

    var resol : Int = 1080
    var cameraid : Boolean = true
    //추가
    private var mTimerTask: TimerTask? = null

    val timer = Timer()

    //카메라 프리뷰
    companion object {

        private const val TAG = "AndroidCameraApi"
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 200

        init {
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_NORMAL,
                0
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_90,
                90
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_180,
                180
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_270,
                270
            )
        }
    }

    private var cameraId: String? = null
    protected var cameraDevice: CameraDevice? = null
    protected var cameraCaptureSessions: CameraCaptureSession? = null
    protected var captureRequest: CaptureRequest? = null
    protected var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val file: File? = null
    private val mFlashSupported = false
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null


    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (binding!!.texture.isAvailable) {
            openCamera()
        } else {
            binding!!.texture.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        stopBackgroundThread()
        super.onPause()
    }

    var textureListener: TextureView.SurfaceTextureListener = object :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            //open your camera here
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            // Transform you image captured size according to the surface width and height
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    @SuppressLint("SimpleDateFormat")
    protected fun takePicture() {
        if (null == cameraDevice) {
            Log.e(
                TAG,
                "cameraDevice is null"
            )
            return
        }
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics =
                manager.getCameraCharacteristics(cameraDevice!!.id)
            var jpegSizes: Array<Size>? = null
            if (characteristics != null) {
                jpegSizes =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.JPEG)
            }
            Log.d("tag", "capture size" + jpegSizes?.get(0))
            var width = 1080
            var height = 1920
            //if (jpegSizes != null && 0 < jpegSizes.size) {
            //    width = resol
            //    height = resol
            //}
            val imageReader =
                ImageReader.newInstance(width, height, ImageFormat.JPEG, 3)
//                ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 5)
            val outputSurfaces: MutableList<Surface> =
                ArrayList(2)
            outputSurfaces.add(imageReader.surface)
            outputSurfaces.add(Surface(binding!!.texture.surfaceTexture))
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface)

            //MANUAL EXPOSURE
            captureBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            );
            captureBuilder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                1000000000L / 60
            );
            captureBuilder.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                100
            )

            // Orientation
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(
                CaptureRequest.JPEG_ORIENTATION,
                ORIENTATIONS[rotation]
            )
            Log.d("camera capture", "success");
//            onPause()
//            onResume()

            //경로 지정
            val absolutePath = "/storage/emulated/0"
            val folderPath = "$absolutePath/Pictures/"

            var timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            var fileName = "${timestamp}.jpg"
            var folder = File(folderPath)
            if (!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면

                Log.d("make directory", "1");
                folder.mkdirs()
                Log.d("make directory", "2");
            }
            Log.d("make directory", "success");

            val file = File(
                folderPath + fileName
            )
            Log.d("file", folderPath + fileName)
//            val readerListener: reader.OnImageAvailableListener = object :
            imageReader.setOnImageAvailableListener({ reader ->
//                Log.d("image reader?","1")
                val image = reader.acquireLatestImage()
//                Log.d("image reader?","2")
                val buffer: ByteBuffer = image.planes[0].buffer
//                Log.d("image reader?","3")
                val bytes = ByteArray(buffer.capacity())
//                Log.d("image reader?","4")
                buffer.get(bytes)
//                Log.d("image reader?","5")
                save(bytes, file)
//                Log.d("image reader?","6")
                image.close()
//                Log.d("image reader?","7")
            }, mBackgroundHandler)
//            onPause()
            Log.d("image reader?", "done")
//            onResume()
            ImageReader.OnImageAvailableListener {
                fun onImageAvailable(reader: ImageReader) {
                    Log.d("before image?", "before image")
                    var image: Image? = null
                    Log.d("after image?", "after image")
                    //var bitmap: Bitmap? = null
                    try {
                        Log.d("start try", "start try")
                        image = reader.acquireLatestImage()
                        Log.d("image read", "image read")
                        val buffer = image.planes[0].buffer
                        Log.d("image read", "image read2")
                        val bytes = ByteArray(buffer.capacity())
                        Log.d("image read", "image read3")
                        buffer[bytes]
                        Log.d("start save", "start save")
                        save(bytes, file)
                        Log.d("complete save", "complete save")
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                        savePhoto(bitmap)
                    } catch (e: FileNotFoundException) {
                        Log.e("save Error", "save Error")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//            @Throws(IOException::class)
//            private fun save(bytes: ByteArray) {
//                Log.d("c", "entered save")
//                //val out = FileOutputStream(file)
//                //bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, out)
//                var output: OutputStream? = null
//                try {
//                    val options: BitmapFactory.Options = BitmapFactory.Options()
//                    options.inPreferredConfig = Bitmap.Config.ARGB_8888
//                    var bitmap: Bitmap =
//                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
//                    var matrix = Matrix()
//                    matrix.postRotate(90f);
//                    Log.d("capture image size", "width" + bitmap.width + "height" + bitmap.height)
//                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, 4032, 3024, matrix, true)
//                    val stream = ByteArrayOutputStream()
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
//                    val currentData: ByteArray = stream.toByteArray()
//
//                    output = FileOutputStream(file)
//                    output.write(currentData)
//                    //output.write(bytes)
//                } finally {
//                    output?.close()
//                    filepath = folderPath + fileName
//                    Log.d("path", filepath)
//                }
//            }
//        }catch (e: CameraAccessException) {
//            e.printStackTrace()
//        }
//    }


//            reader.setOnImageAvailable/**/Listener(readerListener, mBackgroundHandler)
//            val captureListener: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
//                override fun onCaptureCompleted(
//                    session: CameraCaptureSession,
//                    request: CaptureRequest,
//                    result: TotalCaptureResult
//                ) {
//                    super.onCaptureCompleted(session, request, result)
//                    createCameraPreview()
//                }
//            }
//            cameraDevice!!.createCaptureSession(
//                outputSurfaces,
//                object : CameraCaptureSession.StateCallback() {
//                    override fun onConfigured(session: CameraCaptureSession) {
//                        try {
//                            session.capture(
//                                captureBuilder.build(),
//                                captureListener,
//                                mBackgroundHandler
//                            )
//                        } catch (e: CameraAccessException) {
//                            e.printStackTrace()
//                        }
//                    }
//
//                    override fun onConfigureFailed(session: CameraCaptureSession) {}
//                },
//                mBackgroundHandler
//            )
//        } catch (e: CameraAccessException) {
//            e.printStackTrace()
//        }
//    }

    private fun openCamera() {

        Log.d("openCamera","1")
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.e(TAG, "is camera open")
        try {
            var cameraId = manager.cameraIdList[0]
            if (cameraid==true)
                cameraId = manager.cameraIdList[0]
            else
                cameraId = manager.cameraIdList[1]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            Log.d("size", "width" + imageDimension!!.width + "heigth" + imageDimension!!.height)

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }

            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e(TAG, "openCamera X")
    }

    private fun closeCamera() {
        if (null != cameraDevice) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (null != imageReader) {
            imageReader!!.close()
            imageReader = null
        }
    }

    protected fun createCameraPreview() {
        try {
            val texture = binding!!.texture.surfaceTexture!!
            texture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
            Log.d("size", "width" + imageDimension!!.width + "height" + imageDimension!!.height)
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            captureRequestBuilder!!.addTarget(surface)
            cameraDevice!!.createCaptureSession(
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        //The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }
                        // When the session is ready, we start displaying the preview.
                        cameraCaptureSessions = cameraCaptureSession

                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        //Toast.makeText(this, "Configuration change", Toast.LENGTH_SHORT).show();
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(
                    TAG,
                    "updatePreview error, return"
            )
        }

        //MANUAL EXPOSURE
        captureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
        );
        // AE MODE OFF에서만 사용이 가능하다. ns
        captureRequestBuilder!!.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME, // 각 픽셀이 빛에 노출되는 시간을 설정
                1000000000L / 60 /* 30일때 너무 밝음 */
        );

        Log.d(TAG, "nano seconds:"+(1000000000L/30));
        captureRequestBuilder!!.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                100
        );
            try {
                cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
    }

    protected fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setPermission() // 권한을 체크하는 테스트 수행

//        loadModel()

        //카메라 프리뷰
        binding!!.texture.surfaceTextureListener = textureListener
        binding!!.btnTakepicture.setOnClickListener {
            if (mTimerTask != null)
                mTimerTask!!.cancel()
            //수정
            takePicture()

            var num : Int=0
            while ((!(this::filepath.isInitialized))||filepath==null)
                num=1
            Log.d("path!",filepath)
            val nextIntent = Intent(this, ResultActivity::class.java)
            nextIntent.putExtra("filepath", filepath)
            nextIntent.putExtra("preview",checkpreview)
            checkphoto = true
            nextIntent.putExtra("photo",checkphoto)
            nextIntent.putExtra("resol",resol)
            convertfilepath = filepath
            resetField(this, "filepath")
            checkpreview = false
            startActivity(nextIntent)
            //binding.ivPre.isVisible=false
    }

        //binding.ivPre.isVisible=false

        binding.btnGallery.setOnClickListener {
            // 사진 불러오는 함수 실행
            goToAlbum()
            if (this::prebm.isInitialized)
                Log.d("prebm",(prebm.width).toString())
        }

        binding.btnChange.setOnClickListener {
            cameraid=!cameraid
            if (binding!!.texture.isAvailable)
                closeCamera()
            openCamera()

        }

    }

    private fun createTimerTask() : TimerTask{
        val timerTask: TimerTask = object : TimerTask()  {
            override fun run() {
                prebm = binding.texture.getBitmap()!!
                val width = prebm.width
                val height = prebm.height
                val pixels = IntArray(prebm.height * prebm.width)
                prebm.getPixels(pixels, 0, width, 0, 0, width, height)
                //prebm = blur(getApplicationContext(), prebm, 3)
                prebm = Bitmap.createScaledBitmap(prebm, 256, 256, true)


                val begin = System.nanoTime()
                try{
                    Log.d("check", "run")
                    val bmp : Bitmap? = prebm.let { mModule?.let { it1 -> UseModel<Any>(it, it1) } }?.process()!!
                    runOnUiThread{
                        binding.ivPre.setImageBitmap(bmp)
                    }
                    val end = System.nanoTime()
                    Log.d("Elapsed time in nanoseconds: ", "${end-begin}")
                }catch(e:Exception)
                {
                    e.printStackTrace()
                }
            }
        }
        return timerTask;
    }

    private fun resetField(target: Any, fieldName: String) {
        val field = target.javaClass.getDeclaredField(fieldName)

        with (field) {
            isAccessible = true
            set(target, null)
        }
    }

    /*fun blur(context : Context, sentBitmap : Bitmap, radius : Int) : Bitmap{

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            var bitmap : Bitmap = sentBitmap.copy(sentBitmap.getConfig(), true)

            val rs : RenderScript = RenderScript.create(context)
            val input : Allocation = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT)
            val output : Allocation = Allocation.createTyped(rs, input.getType())
            val script : ScriptIntrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius.toFloat()) //0.0f ~ 25.0f
            script.setInput(input)
            script.forEach(output)
            output.copyTo(bitmap)
            return bitmap
        }
        else
            return sentBitmap
    }*/

    // 절대경로 -> uri
    fun getUriFromPath(filePath: String): Uri {
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            null, "_data = '$filePath'", null, null
        )
        if (cursor != null) {
            cursor.moveToNext()
        }
        val id: Int? = cursor?.getInt(cursor?.getColumnIndex("_id"))

        return id?.toLong()?.let {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                it
            )
        }!!
    }

    //bitmap -> uri
    private fun getImageUri(
        context: Context,
        inImage: Bitmap
    ): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            context.contentResolver,
            inImage,
            "Title",
            null
        )
        return Uri.parse(path)
    }

    /**
     *  테드 퍼미션 설정
     */
    private fun setPermission() {
        val permission = object : PermissionListener{
            override fun onPermissionGranted() { // 설정해놓은 위험 권한들이 허용 되었을 경우 수행
                Toast.makeText(this@MainActivity, "권한이 허용 되었습니다.",Toast.LENGTH_SHORT).show()
            }
            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) { // 설정해놓은 위험 권한이 거부 되었을 경우 수행
                Toast.makeText(this@MainActivity, "권한이 거부 되었습니다.",Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.with(this)
            .setPermissionListener(permission)
            .setRationaleMessage("카메라 앱을 사용하시려면 권한을 허용해주세요.")
            .setDeniedMessage("권한을 거부하셨습니다. [앱 설정] -> [권한] 항목에서 허용해주세요.")
            .setPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.CAMERA)
            .check()
    }

    /***
     *  갤러리 이미지 불러오기
     */
    private  fun goToAlbum() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //startActivityForResult를 통해서 기본 카메라 앱으로부터 받아온 사진 결과 값
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == REQUEST_GALLERY_IMAGE && resultCode == Activity.RESULT_OK)
        {
            val uri: Uri? = data?.data
            val bitmap : Bitmap

            getPathFromUri(uri)

            val nextIntent = Intent(this, ResultActivity::class.java)
            checkpreview=true
            nextIntent.putExtra("filepath", filepath)
            nextIntent.putExtra("preview",checkpreview)
            checkphoto=true
            nextIntent.putExtra("photo",checkphoto)
            nextIntent.putExtra("resol",resol)

            // 실행 결과를 저장하여 path 반환 받으면 nextIntent에 넣어서 결과 화면으로 전송
            bitmap = BitmapFactory.decodeFile(nextIntent.getStringExtra("filepath"))
            startActivity(nextIntent)
        }
    }

    // 갤러리에 저장된 사진 불러올 때 Uri - > filepath 구하기
    fun getPathFromUri(uri: Uri?): String? {
        val cursor =
            uri?.let { contentResolver.query(it, null, null, null, null) }
        cursor!!.moveToNext()
        val path = cursor.getString(cursor.getColumnIndex("_data"))
        cursor.close()
        filepath=path
        return path
    }

//    private fun loadModel(){
//        Log.d("function", "entered loadmodel")
//        try{
//            mModule = Module.load(assetFilePath(this,"model4.pt"))
//            Log.d("Model", "Model Loaded Successfully")
//        } catch (e: IOException){
//            Log.e("UseModel", "Load Model Failed", e)
//        }
//    }

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String?): String? {
        val file = File(context.filesDir, assetName!!)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }

    /**
     *  갤러리에 저장
     */
    private fun savePhoto(bitmap: Bitmap) {
        val absolutePath = "/storage/emulated/0/"
        val folderPath = "$absolutePath/Pictures/"
        //val folderPath = Environment.getExternalStorageDirectory().absolutePath + "/Pictures/" // 사진 폴더로 저장하기 위한 경로 선언
        val timestamp =  SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "${timestamp}.jpeg"
        val folder = File(folderPath)
        if(!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면
            folder.mkdirs()
        }

        // 실제적인 저장 처리
        val out =  FileOutputStream(folderPath + fileName)
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
        filepath=folderPath+fileName
    }

    private fun save(bytes:ByteArray, fileName:File){
        try{
            val output = FileOutputStream(fileName)
            output.write(bytes)
            output.close()
        }catch (e: IOException){
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }

    val captureCallbackListener: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            createCameraPreview()
        }
    }
}
