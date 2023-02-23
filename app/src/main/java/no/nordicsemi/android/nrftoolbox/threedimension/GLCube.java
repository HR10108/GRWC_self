package no.nordicsemi.android.nrftoolbox.threedimension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import javax.microedition.khronos.opengles.GL10;

public class GLCube {
    private final IntBuffer mVertexBuffer;   //顶点坐标数据缓冲

    public GLCube() {
        int one = 65530;
        int half = one / 2;
        int oneof5 = one / 5;
        int oneof10 = one / 10;
        int vertices[] = {     //定义顶点位置
           //前面
           -half,-oneof10,oneof5,half,-oneof10,oneof5,-half,oneof10,oneof5,half,oneof10,oneof5,
           //背面
           -half,-oneof10,-oneof5,-half,oneof10,-oneof5,half,-oneof10,-oneof5,half,oneof10,-oneof5,
           //左面
           -half,-oneof10,oneof5,-half,oneof10,oneof5,-half,-oneof10,-oneof5,-half,oneof10,-oneof5,
           //右面
           half,-oneof10,-oneof5,half,oneof10,-oneof5,half,-oneof10,oneof5,half,oneof10,oneof5,
           //上面
           -half,oneof10,oneof5,half,oneof10,oneof5,-half,oneof10,-oneof5,half,oneof10,-oneof5,
           //下面
           -half,-oneof10,oneof5,-half,-oneof10,-oneof5,half,-oneof10,oneof5,half,-oneof10,-oneof5,
        };
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);  //创建顶点坐标数据缓冲
        vbb.order(ByteOrder.nativeOrder());        //设置字节顺序
        mVertexBuffer = vbb.asIntBuffer();       //转换为Int型缓冲
        mVertexBuffer.put(vertices);             //往缓冲中放入顶点坐标数据
        mVertexBuffer.position(0);               //设置缓冲区的起始位置
    }

    public void draw(GL10 gl) {
        gl.glVertexPointer(3,GL10.GL_FIXED,0,mVertexBuffer);  //为画笔指定顶点坐标数据
        //绘制前面
        gl.glColor4f(1,1,0,1);
        gl.glNormal3f(0,0,1);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,0,4);
        //绘制背面
        gl.glColor4f(1,0,0,1);
        gl.glNormal3f(0,0,-1);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,4,4);
        //绘制左面
        gl.glColor4f(0,1,0,1);
        gl.glNormal3f(-1,0,0);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,8,4);
        //绘制右面
        gl.glColor4f(1,0.5f,0,1);
        gl.glNormal3f(1,0,0);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,12,4);
        //绘制上面
        gl.glColor4f(0,0,1,1);
        gl.glNormal3f(0,1,0);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,16,4);
        //绘制下面
        gl.glColor4f(1,0,1,1);
        gl.glNormal3f(0,-1,0);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP,20,4);
    }

}
