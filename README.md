# Tadpole-Tracker
A real-time tracking system that applies computer vision and deep learning to record and analyze movement data of multiple *Xenopus laevis* tadpoles at once for neurobiology research. This is my undergraduate thesis project, conducted in collaboration with the neuroscience department at Bard College.

-----
### Notes


- All the code that performs tracking is called from the SinglePlateTracker class, located [here](src/main/java/sproj/tracking/).

- The thesis paper I wrote for this project is available [here](/paper/senior_thesis.pdf).

- I'm currently translating the system to C++ to run speed benchmarks against Java. That code is in [this repository](https://github.com/alexander-hamme/Tadpole-Tracker-Cpp).

- I may eventually translate the code to Python too, to create wider access for fellow hackers and biology researchers to use for their own projects / research in the future. However, the Python code is much slower than Java. Even with a good GPU it is barely fast enough for real-time tracking (on a GTX 1070 GPU the Yolo network inference runs at 19 fps). My original Python code is functional but requires more work before I can make it available. Some of it is in [this repository](https://github.com/alexander-hamme/Tadpole-Tracker-Python).

-----

### Concepts

There are two major components of this tracker program: Detection and Tracking.

- **Detection** is the process of finding regions of interest (ROI) in each frame (image) from the video input stream.

- **Tracking** is the process of connecting where each animal was in previous frames to its new position in sequential frames, i.e. connecting ROIs to the corresponding tadpoles. This becomes complicated when tracking multiple animals, because of the potential for collisions and collusions. Therefore, algorithms to handle both identity assignment and trajectory prediction are necessary.

### Components

- Deep convolutional neural networks form the tadpole detection component of the overall system. I trained a CNN model to perform xenopus tadpole detection and localization using my own dataset and the [YOLOv2](https://pjreddie.com/darknet/yolov2/) architecture.

- I use linear Kalman filters for trajectory estimation and a modified version of the Munkres Hungarian optimal assignment algorithm for maintaining unique object identities across frames.

-----

<p align="center">
<b>System Overview</b>
<p>
  
![Uh oh, it appears the image  didn't load. Please look at /samples/system_diagram.png in this repositiory.](/sample/system_diagram.png?raw=true "System Diagram")


-----

Speed Benchmarks:

The current iteration of this system runs at ~30 frames/second on a GTX 1070 GPU, which is plenty fast enough for real-time analysis. Using Java with the DeepLearning4J library has provided a significant time speedup from the Python version of this project, which runs at ~19 seconds a frame on the same GPU.
