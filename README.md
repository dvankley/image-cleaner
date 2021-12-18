# Image Cleaner
## What This Is
This program is a rough UI for training an OpenCV object detection Haar classifier with positive and negative samples against a set of
source images, then applying an inpaint operation to all detected objects, and writing the resulting images to files.

It can also add page number annotations, if you're into that sort of thing.

Note that this project was only built to solve a personal problem and as such is pretty half-assed and not really designed for reusability or user
friendliness. Hopefully it's useful to you, but don't expect it or count on it.

## How To Use
### Input
On this page, you configure the working directory (where images, annotations, models, and output will be written and read) and the
input directory (images will be read from the input directory to the source directory inside the working directory).

Click "Load Input Files" to import files from the input directory to the source directory. This will normalize the image
format to `jpg` and is capable of reading images out of PDFs (which is my use case).

### Annotate
This page allows you to annotate each source image with positive or negative annotations. Clicking "Save to File" will
persist the annotations in the format that the OpenCV Haar trainer expects.

Selecting an image that you previously stored annotations for will load previously saved positive annotations, but not
negative annotations because the storage format for negative annotations
makes it hard to map them back to the source image.

The "Positive Annotation Size" spinners will define the size of positive annotation boxes because they're all supposed to
be the same size. Changing it in the midst of the annotation process probably won't work well.

### Train
This was going to be a UI frontend for running OpenCV training, but that was too much work. Feel free to add a UI, or 
just run it from the CLI as I did.

Example commands (in the working directory) for creating positive samples and training a model:
1. `/opt/homebrew/opt/opencv@3/bin/opencv_createsamples -info pos.txt -vec pos/pos.vec -w 150 -h 150`
   1. Where `-w 150 -h 150` is the "Positive Annotation Size" from the annotation tab.
2. `/opt/homebrew/opt/opencv@3/bin/opencv_traincascade -data model/ -vec pos/pos.vec -bg neg.txt -w 150 -h 150`
    1. Where `-vec pos/pos.vec` is the output file from the previous `createsamples` step.
    2. Where `-w 150 -h 150` is the "Positive Annotation Size" from the annotation tab and previous `createsamples` step.

You will likely need to tweak the arguments for the `traincascade` command depending on your circumstances. For instance,
I had a small number of samples so I ended up with `-acceptanceRatioBreakValue 10e-6 -numPos 70 -numNeg 70 -numStages 50`
as described [here](https://answers.opencv.org/question/4368/traincascade-error-bad-argument-can-not-get-new-positive-sample-the-most-possible-reason-is-insufficient-count-of-samples-in-given-vec-file/).

### Test
Loads source images much like the Annotation tab. Allows you to view matched objects based on the trained Haar classifer,
or the hacky "Manual Positive Annotations" mode.

The display mode can be configured to show you matched objects as green boxes, or to show you a preview of what an
inpaint operation on all detected boxes would look like.

In lieu of bothering to build out the whole "Transform" tab, I added the "Inpaint All Source Files" which executes
a batch inpaint operation on all source files based on the currently selected "Match Based On" mode.

"Add Page Numbers" adds page numbers to the outside corner of each page. Ordering is based on my file name convention,
so you should probably examine the underlying code for this before using it.

### Transform
Not implemented.

## Project Story
This project was created when my wife asked me if I could remove some unsightly hole punch artifacts
from a large group of pages she had scanned and wanted to reprint.

My first thought was "hey, maybe I can do this with machine learning!" so that was the first approach I took,
using OpenCV's Haar classifier (apparently the DNN module is the new hotness in terms of object detection, but it
does not currently support training your own model).

The experienced reader will note that training your own object detection model requires a large number (> 1000)
of samples to work effectively, which is only worthwhile if you have a really large number of images to run recognition
against, and even then isn't 100% effective. I only had about 300 subject images, with about 3 objects per page.

I started with about 100 positive samples and 100 negative samples and tried various training configurations for the Haar cascade.
The hope was that the simplicity of the objects being detected (basically just a black circle) would compensate for the
small number of samples.

The results were... underwhelming. While there were not too many false negatives, there were a substantial number of
false positives. Presumably this would have worked fine if I had a sufficient number of samples, but given that I only
have 300 images to run recognition against, gathering more samples for training makes less sense than just manually
marking all the objects.

So that's what I did. At this point the program already had a positive annotation feature, so I configured the program's
object detection to use either the Haar classifier or straight read the positive annotations.

This required a lot of manual drawing of boxes, but resulted in the high level of accuracy required.

Once I had this sorted, I hooked up the OpenCV inpainting feature to remove the detected objects, add a page number annotation
feature because I can, and we were done.

## FAQ
1. Why does the code suck so much?
   1. Because this is a personal project that had a deadline. I only made the project public in the hope that someone
   might find some aspect of it useful. Also: shut up.
2. Why is all the JavaFX logic in one controller?
   1. I tried to split it up, but it didn't work easily and I didn't care enough about the code quality to stick with
   it. See above answer as well.
3. Why didn't you use `opencv_annotation` instead for annotation/sampling?
   1. Because it didn't work on my M1 Mac. Could I have gotten it to work in less time than it took to build a UI from scratch?
   Probably, but that would have been less fun and would have given me less flexibility in adding other features.
