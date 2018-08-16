# Tadpole-Tracker
A computational system that applies computer vision and deep learning to record and analyze movement data of many *Xenopus laevis* tadpoles in real time, for neuroscience research. This is my undergraduate thesis, in collaboration with the neuroscience department at Bard College.

The program will be implemented in both Java and Python, to increase portability (i.e. single executable JAR files) and allow wider access for biology researchers to use in the future. The Python code is a work in progress, available [here](https://github.com/alexander-hamme/Tadpole-Tracker-Python).

-----

There are two major components of this tracker program: **Detection** and **Tracking**.
  * detection is the process of finding regions of interest (ROI) in each frame (image) from the video input stream
  * tracking is the process of connecting where each animal was in previous frames to its new position in sequential frames, 
    i.e. connecting ROIs to the corresponding tadpoles. This becomes complicated when tracking multiple animals, because of the potential for collisions and collusions. Therefore, trajectory prediction algorithms need to be implemented.

Approaches:

  * Detection: Convolutional neural networks will be the building block for the tadpole detection system. I trained deep neural networks for xenopus tadpole detection and localization using the [YOLOv2](https://pjreddie.com/darknet/yolov2/) architecture.

  * Tracking (specifically, trajectory prediction): I will train a Long Short-Term Memory (LSTM) recurrent neural network on recorded tadpole movement data.

-----

Current Progress:

This program runs at approximately 0.9 seconds / frame on my laptop CPU. While still far too slow for real-time analysis, this Java version is a significant time speedup from my Python version of the code, which runs at ~5 seconds a frame. The next step is to enable GPU acceleration with CUDA and try to reach at least 20-25 frames per second.

![Uh oh, it appears the image  didn't load. Please find the proof of concpet at /samples/tracking.png in this repositiory.](/sample/tracker.png?raw=true "Proof of Concept")
