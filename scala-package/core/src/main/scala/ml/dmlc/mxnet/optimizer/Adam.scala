import ml.dmlc.mxnet.{NDArray, Optimizer, LRScheduler}
import ml.dmlc.mxnet.NDArrayConversions._

/**
 * Adam optimizer as described in [King2014]
 *
 * [King2014] Diederik Kingma, Jimmy Ba,
 * Adam: A Method for Stochastic Optimization,
 * http://arxiv.org/abs/1412.6980
 *
 * @param learningRate Float, Step size.
 * @param beta1 Float, Exponential decay rate for the first moment estimates.
 * @param beta2 Float, Exponential decay rate for the second moment estimates.
 * @param epsilon Float
 * @param decayFactor Float
 * @param wd Float, L2 regularization coefficient add to all the weights
 * @param rescaleGrad Float, rescaling factor of gradient.
 * @param clipGradient Float, clip gradient in range [-clip_gradient, clip_gradient]
 * @param lrScheduler The learning rate scheduler
 */
class Adam(var learningRate: Float = 0.002f, val beta1: Float = 0.9f, val beta2: Float = 0.999f,
          val epsilon: Float = 0.00000001f, val decayFactor: Float = 1-0.00000001f, val wd: Float = 0.0f,
           rescaleGrad: Float = 1f, val clipGradient: Float = 0f,
          val lrScheduler: LRScheduler = null) extends Optimizer(rescaleGrad: Float) {

  protected var time: Int = 0
  protected var timeFirstIndex: Int = 0
  /**
   * Update the parameters.
   * @param index An unique integer key used to index the parameters
   * @param weight weight ndarray
   * @param grad grad ndarray
   * @param state NDArray or other objects returned by initState
   *              The auxiliary state used in optimization.
   */
  override def update(index: Int, weight: NDArray, grad: NDArray, state: AnyRef): Unit = {
    val lr =
      (if (lrScheduler != null) {
        val scheduledLr = lrScheduler(numUpdate)
        updateCount(index)
        scheduledLr
      } else {
        this.learningRate
      }) * lrScale.getOrElse(index, 1f)

    var (mean, variance)  = state

    if (timeFirstIndex == 0) {
      timeFirstIndex = index
      time = 0
    } else if (timeFirstIndex == index) {
      time += 1
    }

    val t1: Int = time + 1
    learningRate = (lr * math.sqrt(1.0 - math.pow(beta2, t1))/(1.0 - math.pow(beta1, t1))) toFloat
    val beta1t = beta1 * math.pow(decayFactor, t1 - 1) toFloat

    var resdGrad = grad * rescaleGrad
    if (clipGradient != 0f) {
      resdGrad = NDArray.clip(resdGrad, -clipGradient, clipGradient)
    }

    val meanT = beta1t * mean.asInstanceOf[NDArray] + (1.0 - beta1t) * resdGrad toScalar
    val varianceT = beta2 * variance.asInstanceOf[NDArray] + (1.0f - beta2) * resdGrad * resdGrad toScalar

    var step = learningRate * meanT / (math.sqrt(varianceT) + epsilon)

    if (wd > 0.0f) {
      step += lr * wd * weight
    }

    weight += -step.toFloat
    mean = meanT
    variance = varianceT
  }

  // Create additional optimizer state: mean, variance
  override def createState(index: Int, weight: NDArray): AnyRef = {
    timeFirstIndex = 0
    (NDArray.zeros(weight.shape, weight.context), // mean
      NDArray.zeros(weight.shape, weight.context)) // variance
  }
}
