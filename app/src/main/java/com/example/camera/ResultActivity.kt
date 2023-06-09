package com. example.camera

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.camera.LoadingDialog
import com.example.camera.R
import com.example.camera.UseModel
import com.example.camera.databinding.ActivityResultBinding
import github.com.st235.lib_expandablebottombar.ExpandableBottomBar
import github.com.st235.lib_expandablebottombar.MenuItemDescriptor
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class ResultActivity : AppCompatActivity() {
    private var mBinding : ActivityResultBinding? = null
    private  val binding get() = mBinding!!
    private var mModule: Module? = null
    var bm : Bitmap? = null;
    lateinit var bmp : Bitmap
    val REQUEST_GALLERY_IMAGE = 1 // 갤러리 이미지 불러오기
    val loadingDialog = LoadingDialog(this)
    var checkpreview : Boolean = false
    var checkphoto : Boolean = false
    var origianlBrightness : Int =0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivInput.isVisible = false
        binding.ivOutput.isVisible = false
        binding.expandableBottomBar.isVisible = false

        loadModel()

        class logic(context: Context?) :
                AsyncTask<Bitmap?, Bitmap?, Bitmap?>() {
            private var mContext: Context? = null

            init {
                mContext = context
            }

            override fun doInBackground(vararg params: Bitmap?): Bitmap? {
                Log.d("check ","entered doinbackground")
                var img : Bitmap? = null
                try {
                    bmp = params[0]?.let { mModule?.let { it1 -> UseModel<Any>(it, it1) } }?.process()!!
                    img = bmp
                    Log.d("check", "got the img")
                    publishProgress(bmp)
                } catch(e:Exception) {
                    e.printStackTrace()
                }
                return img
            }

            override fun onProgressUpdate(vararg bmp: Bitmap?) {
                binding.ivInput.isVisible = true
                binding.expandableBottomBar.isVisible = true
                binding.ivInput.setImageBitmap(bmp[0])
            }

            override fun onPostExecute(result: Bitmap?) {
                loadingDialog.dismissDialog()
            }

            override fun onPreExecute() {
                loadingDialog.startLoadingDialog()
            }
        }

        if (intent.hasExtra("filepath")) {
           // Toast.makeText(this, intent.getStringExtra("filepath") ,Toast.LENGTH_SHORT).show()
            bm = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))
            var resol : Int = 720
            Log.d("intent", intent.getStringExtra("filepath").toString())
            if (intent.hasExtra("resol"))
                    resol  = intent.getIntExtra("resol",720)
            /*if(bm!!.width !=resol || bm!!.height != resol) {
                //블러 강도 결정
                var blurRadius: Int = 1 //.toFloat()
                bm = blur(getApplicationContext(), bm!!, blurRadius)
                bm = Bitmap.createScaledBitmap(bm!!, resol, resol, true)
            }*/
            //origianlBrightness = calculateBrightness(bm!!)
            if (intent.hasExtra("preview"))
            {
                checkpreview = intent.getBooleanExtra("preview",true)
                checkphoto = intent.getBooleanExtra("photo",false)

                if (checkphoto) {
                    var task = logic(this)
                    task.execute(bm)
                }
                else {
                    if (checkpreview) {
                        var task = logic(this)
                        task.execute(bm)

                    } else {
                        binding.ivInput.isVisible = true
                        binding.ivInput.setImageBitmap(bm)
                        binding.expandableBottomBar.isVisible = true
                        bmp = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))


                    }
                }
            }
        }
        else {
            Toast.makeText(this, "filepathError!", Toast.LENGTH_SHORT).show()
        }

        val bottomBar: ExpandableBottomBar = findViewById(R.id.expandable_bottom_bar)

        val menu = bottomBar.menu

        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_gallery,
                R.drawable.ic_photo_library_black_24dp,
                R.string.gallery, Color.parseColor("#9370DB")
            )
                .build()
        )
        //share 로 바꾸기
        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_share,
                R.drawable.ic_share_svgrepo_com,
                R.string.share, Color.YELLOW
            )
                .build()
        )

        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_save,
                R.drawable.ic_save_alt_black_24dp,
                R.string.save,
                Color.parseColor("#58a5f0")
            )
                .build()
        )

        bottomBar.onItemSelectedListener = { v, i, _ ->
            if(i.text == "gallery"){
                goToAlbum()
            }
            else if(i.text == "save"){
                savePhoto(bmp)
                Toast.makeText(this,"Saved!",Toast.LENGTH_SHORT).show()
            }
            //detail 버튼 말고 share
            else if(i.text=="share"){
                share(bmp) //show()
            }

        }

        bottomBar.onItemReselectedListener = { _, i, _ ->
            if(i.text == "gallery"){
                goToAlbum()
            }
            else if(i.text == "save"){
                savePhoto(bmp)
                Toast.makeText(this,"Saved!",Toast.LENGTH_SHORT).show()
            }
            // detail 말고 share 버튼
            else if(i.text=="share"){
                share(bmp) //show()
            }
        }

        binding.ivInput.setOnTouchListener(View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bm = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))
                    binding.ivOutput.setImageBitmap(bm)
                    binding.ivInput.isVisible = false
                    binding.ivOutput.isVisible = true
                }
                MotionEvent.ACTION_UP -> {
                    binding.ivInput.isVisible = true
                    binding.ivOutput.isVisible = false
                }
            }
            false
        })
    }
    //이거 바꾸기
    /*fun show() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Details")

        if (checkphoto) {
            builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nOriginal Brightness : "+ origianlBrightness.toString()+"\nBrightness : "+ calculateBrightness(bmp).toString())
        }
        else {
            if (checkpreview) {
                builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nOriginal Brightness : "+ origianlBrightness.toString()+"\nBrightness : "+ calculateBrightness(bmp).toString())

            } else {
                builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nBrightness : "+ calculateBrightness(bmp).toString())

            }
        }

        builder.setPositiveButton("확인",
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                    }
                })
        builder.show()
    }*/
    /*처리된 사진을 공유하는 코드*/
    private fun share(bitmap: Bitmap) {
        val intent = Intent(Intent.ACTION_SEND)
        val uri: Uri? = getImageUri(this, bitmap)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        val chooser = Intent.createChooser(intent, "share")
        startActivity(chooser)
    }

    /*fun calculateBrightnessEstimate(bitmap: Bitmap, pixelSpacing: Int): Int {
        var R = 0
        var G = 0
        var B = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            R += Color.red(color)
            G += Color.green(color)
            B += Color.blue(color)
            n++
            i += pixelSpacing
        }
        return (R + B + G) / (n * 3)
    }

    fun calculateBrightness(bitmap: Bitmap): Int {
        return calculateBrightnessEstimate(bitmap, 1)
    }*/

    private  fun goToAlbum() {

        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }

    private fun loadModel(){
        try{
            mModule = Module.load(assetFilePath(this, "model4.pt"))
            Log.d("Model", "Model Loaded Successfully")
        } catch (e: IOException){
            Log.e("UseModel", "Load Model Failed", e)
        }
    }

    /**
     * return : model의 절대경로
     */
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

    //bitmap to uri
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

    override fun onDestroy() {
        // onDestroy 에서 binding class 인스턴스 참조를 정리해주어야 한다.
        mBinding = null
        super.onDestroy()
    }

    private fun savePhoto(bitmap: Bitmap) {
        val absolutePath = "/storage/emulated/0/"
        val folderPath = "$absolutePath/Pictures/Result/"
        val timestamp =  SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "${timestamp}.jpeg"
        val folder = File(folderPath)
        if(!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면
            folder.mkdirs()
        }

        checkpreview = intent.getBooleanExtra("preview",true)
        checkphoto = intent.getBooleanExtra("photo",false)
        // 앨범/프리뷰 확인
        // 프리뷰 -> 인텐트 옮길때 받은 filepath에 저장하기
        // 앨범 -> folderPath+ fileName에 저장하기
        if(checkphoto)
        {
            val out =  FileOutputStream(folderPath + fileName)
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
            //sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+folderPath+fileName)))
        }
        else
        {
            val out =  FileOutputStream(intent.getStringExtra("filepath"))
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
            //sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+intent.getStringExtra("filepath"))))
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
        else return sentBitmap
    }*/

    /*fun RemoveNoise(bmap: Bitmap): Bitmap? {
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R < 162 && G < 162 && B < 162) bmap.setPixel(
                    x,
                    y,
                    Color.BLACK
                )
            }
        }
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R > 162 && G > 162 && B > 162) bmap.setPixel(
                    x,
                    y,
                    Color.WHITE
                )
            }
        }
        return bmap
    }*/
}