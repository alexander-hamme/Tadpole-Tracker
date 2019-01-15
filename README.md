# Tadpole-Tracker
A computational system that applies computer vision and deep learning to record and analyze movement data of many *Xenopus laevis* tadpoles in real time, for neuroscience research. This is my undergraduate thesis, in collaboration with the neuroscience department at Bard College.

-----
Notes: 

-- recent work is on the "dev" branch of this project.

-- the research paper for this project is also available [in this repository](/paper/senior_thesis.pdf).

-- While I am primarily conducting my research in Java at the moment, I will eventually translate it to Python as well, to allow wider access for biology researchers and developers to use and hack in the future. The Python code is functional but requires more work before it can be fully released. Some of it is available [here](https://github.com/alexander-hamme/Tadpole-Tracker-Python).


-----

There are two major components of this tracker program: **Detection** and **Tracking**.
  * detection is the process of finding regions of interest (ROI) in each frame (image) from the video input stream
  * tracking is the process of connecting where each animal was in previous frames to its new position in sequential frames, 
    i.e. connecting ROIs to the corresponding tadpoles. This becomes complicated when tracking multiple animals, because of the potential for collisions and collusions. Therefore, trajectory prediction algorithms need to be implemented.

Approaches:

  * Detection: Convolutional neural networks form the tadpole detection component of the overall system. I trained deep neural networks for xenopus tadpole detection and localization using the [YOLOv2](https://pjreddie.com/darknet/yolov2/) architecture.

  * Tracking (specifically, trajectory prediction): I have implemented linear Kalman filters for trajectory estimation, and a modified version of the Munkres Hungarian optimal assignment algorithm for maintaining unique object identities across frames.

-----

Benchmarks:

This program runs at ~30 frames/second on a GTX 1070 GPU, which is plenty fast enough for real-time analysis. Using Java with the DeepLearning4J library has provided a significant time speedup from the Python version of this project, which runs at ~19 seconds a frame on the same GPU.

![Uh oh, it appears the image  didn't load. Please find the proof of concept at /samples/tracking.png in this repositiory.](/sample/tracker.png?raw=true "Proof of Concept")
